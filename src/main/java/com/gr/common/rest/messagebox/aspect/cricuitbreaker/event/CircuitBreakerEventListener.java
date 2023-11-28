package com.gr.common.rest.messagebox.aspect.cricuitbreaker.event;

import com.gr.common.rest.messagebox.config.WebClientFactory;
import com.gr.common.rest.messagebox.constants.RestMessageStatus;
import com.gr.common.rest.messagebox.entity.RestMessage;
import com.gr.common.rest.messagebox.service.RestMessageService;
import com.gr.common.util.Util;
import com.gr.grid.common.util.JsonUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;


@Component
@Slf4j
public class CircuitBreakerEventListener {

    @Autowired
    private RestMessageService restMessageService;


    @Autowired
    private WebClientFactory webClientFactory;



    protected void processOutboxOnClosedState() {
        List<RestMessage> pendingMessages = restMessageService.getPendingOutboxMessages();
        log.info("processing outbox messages on circuit CLOSE, size {}", pendingMessages.size());

        pendingMessages.stream().forEach(message -> {
            log.info("processing outbox message ID: {}, name :{}", message.getId(), message.getServiceMethodName());

            processMessageAgain(message);
        });
    }

    // Semaphore used to prevent multiple executions of processing when the circuit transitions to the closed state.
    // Multiple instances may register for the transition event due to their respective fallbacks.
    // The semaphore ensures that only one execution of processing all outbox messages is allowed, which is sufficient to process all the messages.
    private final Semaphore semaphore = new Semaphore(1);


    public void circuitBreakerTransitionEventListener(CircuitBreakerOnStateTransitionEvent event) {
        log.info("Circuit Breaker state transitioned event from " + event.getStateTransition().getFromState() + " to " + event.getStateTransition().getToState());
        if (event.getStateTransition().getToState().equals(CircuitBreaker.State.CLOSED)) {
            try {
                // Acquire the semaphore to prevent other threads from processing again. the reason explained on semaphore initialization
                semaphore.acquire();
                processOutboxOnClosedState();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Handle interrupted exception if needed
            } finally {
                semaphore.release(); // Release the semaphore to allow other threads to access the critical section
            }
        }
    }

    public void processOutboxMessagesFirst(String methodName) {
        List<RestMessage> pendingMessages = restMessageService.getPendingOutboxMessages(methodName);
        log.info("processing outbox message existing fo {}, size {}",methodName, pendingMessages.size());
        pendingMessages.stream().forEach(message -> {
            processMessageAgain(message);
        });
    }

    private void processMessageAgain(RestMessage message) {
        log.info("processing outbox message ID {}", message.getId());

        WebClient webClient = webClientFactory.createWebClient(message.getEndPointUrl());
        switch (message.getHttpMethod()){
            case GET:
                webClient.post().header("Content-Type", "application/json")
                        .bodyValue(message.getContent())
                        .retrieve()
                        .onStatus(HttpStatus::isError, response -> {
                            // Handle Reprocessing on error
                            log.error("Error processing GET request. Response status: {}", response.statusCode());
                            message.setResponseStatusMessage(response.statusCode().getReasonPhrase());
                            message.setReponseStatusCode(response.statusCode().value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .onStatus(HttpStatus::is2xxSuccessful, response -> {
                            // Set status as SUCCESS on successful call
                            message.setStatus(RestMessageStatus.SUCCESS);
                            message.setReponseStatusCode(HttpStatus.OK.value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .bodyToMono(Void.class)
                        .block();
                break;
            case POST:
                webClient.post()
                        .header("Content-Type", "application/json")
                        .bodyValue(message.getContent())
                        .retrieve().onStatus(HttpStatus::isError, response -> {
                            // Handle Reprocessing on error
                            log.error("Error processing POST request. Response status: {}", response.statusCode());
                            message.setResponseStatusMessage(response.statusCode().getReasonPhrase());
                            message.setReponseStatusCode(response.statusCode().value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .onStatus(HttpStatus::is2xxSuccessful, response -> {
                            // Set status as SUCCESS on successful call
                            message.setStatus(RestMessageStatus.SUCCESS);
                            message.setReponseStatusCode(HttpStatus.OK.value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .bodyToMono(Void.class)
                        .block();
                break;
            case PUT:
                webClient.put().header("Content-Type", "application/json")
                        .bodyValue(message.getContent())
                        .retrieve().onStatus(HttpStatus::isError, response -> {
                            // Handle error response
                            log.error("Error processing PUT request. Response status: {}", response.statusCode());
                            message.setResponseStatusMessage(response.statusCode().getReasonPhrase());
                            message.setReponseStatusCode(response.statusCode().value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .onStatus(HttpStatus::is2xxSuccessful, response -> {
                            // Set status as SUCCESS on successful call
                            message.setStatus(RestMessageStatus.SUCCESS);
                            message.setReponseStatusCode(HttpStatus.OK.value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .bodyToMono(Void.class)
                        .block();
                break;
            case DELETE:
                webClient.delete().retrieve().onStatus(HttpStatus::isError, response -> {                     // Handle error response
                            log.error("Error processing DELETE request. Response status: {}", response.statusCode());
                            message.setResponseStatusMessage(response.statusCode().getReasonPhrase());
                            message.setReponseStatusCode(response.statusCode().value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .onStatus(HttpStatus::is2xxSuccessful, response -> {
                            // Set status as SUCCESS on successful call
                            message.setStatus(RestMessageStatus.SUCCESS);
                            message.setReponseStatusCode(HttpStatus.OK.value());
                            message.setRetryCount(message.getRetryCount() + 1);
                            restMessageService.saveRestMessage(message);
                            return Mono.empty();
                        })
                        .bodyToMono(Void.class)
                        .block();
        }

    }
}
