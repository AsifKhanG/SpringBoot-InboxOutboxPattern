package com.gr.common.rest.messagebox.aspect.cricuitbreaker.aspect;

import com.gr.common.rest.messagebox.aspect.cricuitbreaker.event.CircuitBreakerEventListener;
import com.gr.common.rest.messagebox.constants.*;
import com.gr.common.rest.messagebox.entity.RestMessage;
import com.gr.common.rest.messagebox.service.RestMessageService;
import com.gr.common.util.Util;
import com.gr.grid.common.util.JsonUtil;
import com.gr.sync.common.exception.RestClientException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.gr.common.rest.messagebox.aspect.cricuitbreaker.aspect.SupplierUtil.rethrowSupplier;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;


@Aspect
@Slf4j
@Component
public class FaultTolerantAspect {

    @Autowired
    private RestMessageService restMessageService;

    @Autowired
    private CircuitBreakerEventListener circuitBreakerEventListener;

    private final CircuitBreaker circuitBreaker;

    @Autowired
    private Environment env;


    // Flag to track if outbox messages have been processed for the current request
    private final ThreadLocal<Boolean> outboxProcessed = ThreadLocal.withInitial(() -> false);

    private Boolean transitionEventListenerRequired = Boolean.TRUE;

    private GrSystem sourceSystem;


    @Value("${resilience4j.retry.maxAttempts:10}")
    private Integer maxAttempts;
//
    @Value("${resilience4j.retry.waitDuration:1000}")
    private Long waitDuration;
//
//    @Value("${circuit.breaker.name}")
//    private String circuitBreakerName;

    private final Retry retry ;


    public FaultTolerantAspect(CircuitBreakerRegistry circuitBreakerRegistry, @Value("${resilience4j.retry.maxAttempts}") Integer maxAttempts, @Value("${resilience4j.retry.waitDuration}") final Integer waitDuration) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("commService");

        log.info("maxAttempts: " + maxAttempts);
        log.info("waitDuration: " + waitDuration);
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofSeconds(waitDuration))
                .build();

        this.retry = Retry.of("retryService", retryConfig);

        // Add event consumer to log retry events
        this.retry.getEventPublisher()
                .onRetry(evt -> {
                    log.info("Retry attempt #{} for method {}", evt.getNumberOfRetryAttempts(), evt.getName());
                })
                .onError(evt -> log.error("Retry error for method {} after {} attempts", evt.getName(), evt.getNumberOfRetryAttempts()))
                .onSuccess(evt -> log.info("Retry success for method {} after {} attempts", evt.getName(), evt.getNumberOfRetryAttempts()))
                .onIgnoredError(evt -> log.warn("Retry ignored an error for method {} after {} attempts", evt.getName(), evt.getNumberOfRetryAttempts()))
                .onEvent(evt -> log.debug("Retry event {} for method {}", evt.getEventType(), evt.getName()));
    }

    @Before("@annotation(restFaultTolerant)")
    public void before(RestFaultTolerant restFaultTolerant) {
        this.sourceSystem = restFaultTolerant.sourceSystem();
        this.waitDuration = restFaultTolerant.waitDuration();
        this.maxAttempts = restFaultTolerant.maxAttempts();
        // If outbox message found, process all the pending outbox messages first
        //if initialState is zero process outbox message at first-hand then proceed to the incoming message.
        if(Boolean.TRUE.equals(restFaultTolerant.processOutBoxMessagesFirst()) && Boolean.FALSE.equals(outboxProcessed.get())) {
            outboxProcessed.set(Boolean.TRUE);
            circuitBreakerEventListener.processOutboxMessagesFirst(restFaultTolerant.methodName());
        }
        this.transitionEventListenerRequired = restFaultTolerant.transitionEventListenerRequired();
    }

    @Around("@annotation(restFaultTolerant)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint, RestFaultTolerant restFaultTolerant) throws Throwable {
        // Get the requestDto from method arguments
        Object[] args = proceedingJoinPoint.getArgs();
        Object requestDto = args.length > 0 ? args[0] : null;

        String name = restFaultTolerant.methodName();
        //get base url of downstream/target service
        String targetServiceURL = env.getProperty(restFaultTolerant.targetServiceBaseUrlProperty());

        String url = targetServiceURL + restFaultTolerant.targetURI();
        ServiceName serviceName = restFaultTolerant.serviceName();


//        Object response =execute(rethrowSupplier(proceedingJoinPoint::proceed), ex -> recoverMessageIncaseOfFallBack(serviceName, name, url, requestDto, ex));
        Object response = execute(rethrowSupplier(proceedingJoinPoint::proceed),
                ex -> recoverMessageIncaseOfFallBack(serviceName, name, url, requestDto, ex));
        log.info("Circuit Breaker Aspect completed execution of class - {}, method - {} endpoint - {}", proceedingJoinPoint.getSignature().getDeclaringType().getName(), proceedingJoinPoint.getSignature().getName(), url);

        return response;
    }

    private <T> T execute(Supplier<T> supplier, Consumer<Throwable> fallback) {
        try {
            return Decorators.ofSupplier(supplier)
                    .withRetry(retry)
                    .withCircuitBreaker(circuitBreaker)
                    .get();
        } catch (Throwable throwable) {
            fallback.accept(throwable);
            throw throwable; // Rethrow the original exception.
        }
    }


    // fall method as recovery handler using outbox design pattern.
    public <T> Object recoverMessageIncaseOfFallBack(ServiceName serviceName, String methodName, String url, Object requestDto, Throwable throwable) {

            if (transitionEventListenerRequired) {
                circuitBreaker.getEventPublisher().onStateTransition(circuitBreakerEventListener::circuitBreakerTransitionEventListener);
            }

            RestMessage restMessage;
            if (throwable instanceof RestClientException) {
                RestClientException restClientException = (RestClientException) throwable;
                restMessage = RestMessage.builder()
                        .content(JsonUtil.toJson(requestDto))
                        .contentType("JSON")
                        .endPointUrl(restClientException.getUrl())
                        .httpMethod(HTTPMethod.valueOf(restClientException.getMethodType()))
                        .reponseStatusCode(restClientException.getHttpCode())
                        .responseStatusMessage(restClientException.getMessage())
                        .response(restClientException.getReason())
                        .responseDateTime(restClientException.getTimeStamp())
                        .type(Type.OUTBOX)
                        .createDateTime(LocalDateTime.now())
                        .responseDurationInMillis(restClientException.getResponseDuration())
                        .responseHeadersJson(JsonUtil.toJson(restClientException.getResponseHeaderJson()))
                        .requestHeadersJson(JsonUtil.toJson(restClientException.getRequestHeaderJson()))
                        .status(RestMessageStatus.FAILURE)
                        .sourceSystem(GrSystem.GRID2)
                        .sourceService(serviceName)
                        .serviceMethodName(methodName)
                        .build();

            } else if (throwable instanceof CallNotPermittedException) {

                CallNotPermittedException callNotPermittedException = (CallNotPermittedException) throwable;
                // The circuit breaker is open and did not allow the method execution
                restMessage = RestMessage.builder()
                        .content(JsonUtil.toJson(requestDto))
                        .contentType("JSON")
                        .endPointUrl(url)
                        .httpMethod(HTTPMethod.POST)
                        .reponseStatusCode(SERVICE_UNAVAILABLE.value())
                        .responseStatusMessage(callNotPermittedException.getMessage())
                        .response(SERVICE_UNAVAILABLE.getReasonPhrase())
                        .responseDateTime(LocalDateTime.now())
                        .type(Type.OUTBOX)
                        .createDateTime(LocalDateTime.now())
                        .responseDurationInMillis(0L)
                        .responseHeadersJson(null)
                        .requestHeadersJson(null)
                        .status(RestMessageStatus.FAILURE)
                        .sourceSystem(GrSystem.GRID2)
                        .sourceService(serviceName)
                        .serviceMethodName(methodName)
                        .build();
            } else {
                restMessage = RestMessage.builder()
                        .content(JsonUtil.convertToJson(requestDto))
                        .contentType("JSON")
                        .endPointUrl(url)
                        .httpMethod(HTTPMethod.POST)
                        .reponseStatusCode(SERVICE_UNAVAILABLE.value())
                        .responseStatusMessage(throwable.getMessage())
                        .response(SERVICE_UNAVAILABLE.getReasonPhrase())
                        .responseDateTime(LocalDateTime.now())
                        .type(Type.OUTBOX)
                        .createDateTime(LocalDateTime.now())
                        .responseDurationInMillis(0L)
                        .responseHeadersJson(null)
                        .requestHeadersJson(null)
                        .status(RestMessageStatus.FAILURE)
                        .sourceSystem(GrSystem.GRID2)
                        .sourceService(serviceName)
                        .serviceMethodName(methodName)
                        .build();

            }
            restMessage.setRetryCount(Util.isNull(restMessage.getRetryCount()) ? 0 : restMessage.getRetryCount() + 1);
            restMessageService.saveRestMessage(restMessage);
            return null;
    }

}
