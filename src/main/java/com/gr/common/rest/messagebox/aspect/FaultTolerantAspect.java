package com.gr.common.rest.messagebox.aspect;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.gr.common.rest.messagebox.aspect.SupplierUtil.rethrowSupplier;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;


@Aspect
@Component
@Slf4j
public class FaultTolerantAspect {

    @Autowired
    private RestMessageService restMessageService;

    private final CircuitBreaker circuitBreaker;

    @Value("${resilience4j.retry.maxAttempts}")
    private Integer maxAttempts;

    @Value("${resilience4j.retry.waitDuration}")
    private Long waitDuration;

    private final Retry retry ;

    public FaultTolerantAspect(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("syncService");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .build();

        this.retry = Retry.of("retryService", retryConfig);
    }



    @Around("@annotation(faultTolerant)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint, FaultTolerant faultTolerant) {
        // Get the requestDto from method arguments
        Object[] args = proceedingJoinPoint.getArgs();
        Object requestDto = args.length > 0 ? args[0] : null;

        String name = faultTolerant.name();
        String url = faultTolerant.url();
        RestMessage.ServiceName serviceName = faultTolerant.serviceName();

        execute(rethrowSupplier(proceedingJoinPoint::proceed), ex -> recoverMessageIncaseOfFallBack(serviceName, name, url, requestDto, ex));

        log.info("Circuit Breaker Aspect completed execution of class - {}, method - {} endpoint - {}", proceedingJoinPoint.getSignature().getDeclaringType().getName(), proceedingJoinPoint.getSignature().getName(), url);

        return null;
    }

    private <T> T execute(Supplier<T> supplier, Function<Throwable, T> fallback) {
        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker)
                .withFallback(fallback)
//                .withRetry(retry)
                .get();
    }


    // fall method as recovery handler using outbox design pattern.
    public <T> Object recoverMessageIncaseOfFallBack(RestMessage.ServiceName serviceName, String name, String url, T requestDto, Throwable throwable) {

        if (throwable instanceof RestClientException) {
            RestClientException restClientException = (RestClientException) throwable;
            RestMessage restMessage = RestMessage.builder()
                    .content(JsonUtil.toJson(requestDto))
                    .contentType("JSON")
                    .endPointUrl(Util.isNotNullAndEmpty(restClientException.getUrl()) ? restClientException.getUrl() : url)
                    .httpMethod(RestMessage.HTTPMethod.valueOf(restClientException.getMethodType()))
                    .reponseStatusCode(restClientException.getHttpCode())
                    .responseStatusMessage(restClientException.getMessage())
                    .response(restClientException.getReason())
                    .responseDateTime(restClientException.getTimeStamp())
                    .type(RestMessage.Type.OUTBOX)
                    .createDateTime(LocalDateTime.now())
                    .responseDurationInMillis(restClientException.getResponseDuration())
                    .responseHeadersJson(JsonUtil.toJson(restClientException.getResponseHeaderJson()))
                    .requestHeadersJson(JsonUtil.toJson(restClientException.getRequestHeaderJson()))
                    .status(RestMessage.Status.FAILURE)
                    .sourceSystem(RestMessage.GrSystem.GRID2)
                    .sourceService(serviceName)
                    .serviceMethodName(name)
                    .build();
            restMessageService.saveRestMessage(restMessage);
        } else if (throwable instanceof CallNotPermittedException) {

            CallNotPermittedException callNotPermittedException = (CallNotPermittedException) throwable;
            // The circuit breaker is open and did not allow the method execution
            RestMessage restMessage = RestMessage.builder()
                    .content(JsonUtil.toJson(requestDto))
                    .contentType("JSON")
                    .endPointUrl(url)
                    .httpMethod(RestMessage.HTTPMethod.POST)
                    .reponseStatusCode(SERVICE_UNAVAILABLE.value())
                    .responseStatusMessage(callNotPermittedException.getMessage())
                    .response(SERVICE_UNAVAILABLE.getReasonPhrase())
                    .responseDateTime(LocalDateTime.now())
                    .type(RestMessage.Type.OUTBOX)
                    .createDateTime(LocalDateTime.now())
                    .responseDurationInMillis(0L)
                    .responseHeadersJson(null)
                    .requestHeadersJson(null)
                    .status(RestMessage.Status.FAILURE)
                    .sourceSystem(RestMessage.GrSystem.GRID2)
                    .sourceService(serviceName)
                    .serviceMethodName(name)
                    .build();
        } else {

            RestMessage restMessage = RestMessage.builder()
                    .content(JsonUtil.toJson(requestDto))
                    .contentType("JSON")
                    .endPointUrl(url)
                    .httpMethod(RestMessage.HTTPMethod.POST)
                    .reponseStatusCode(SERVICE_UNAVAILABLE.value())
                    .responseStatusMessage(throwable.getMessage())
                    .response(SERVICE_UNAVAILABLE.getReasonPhrase())
                    .responseDateTime(LocalDateTime.now())
                    .type(RestMessage.Type.OUTBOX)
                    .createDateTime(LocalDateTime.now())
                    .responseDurationInMillis(0L)
                    .responseHeadersJson(null)
                    .requestHeadersJson(null)
                    .status(RestMessage.Status.FAILURE)
                    .sourceSystem(RestMessage.GrSystem.GRID2)
                    .sourceService(serviceName)
                    .serviceMethodName(name)
                    .build();
            restMessageService.saveRestMessage(restMessage);
        }
        return null;
    }
}
