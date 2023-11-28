package com.gr.common.rest.messagebox.aspect.cricuitbreaker.aspect;

import com.gr.common.rest.messagebox.constants.GrSystem;
import com.gr.common.rest.messagebox.constants.ServiceName;
import com.gr.common.rest.messagebox.entity.RestMessage;
import org.springframework.beans.factory.annotation.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RestFaultTolerant {
    ServiceName serviceName();
    String methodName() default "";
    String targetServiceBaseUrlProperty() default "";
    String targetURI() default "";
    GrSystem sourceSystem() default GrSystem.GRID2;

    //When set true, on each circuit breaker transitioned to closed state, It will process all the outbox rest messages.
    boolean transitionEventListenerRequired() default false;

    //Making sure ordering of messages: When set true it will first process all outbox rest message for the same method.
    boolean processOutBoxMessagesFirst() default false;

    @Value("${resilience4j.retry.waitDuration}")
    long waitDuration() default 0;

    @Value("${resilience4j.retry.maxAttempts}")
    int maxAttempts() default 0;

}


