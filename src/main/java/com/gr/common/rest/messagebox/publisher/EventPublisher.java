package com.gr.common.rest.messagebox.publisher;

import com.gr.common.rest.messagebox.constants.RequestProcessingStatus;
import com.gr.common.rest.messagebox.constants.ServiceName;
import com.gr.common.rest.messagebox.dto.GenericInboxEventDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void publishEventForInbox(final String id, final Object requestDto, ServiceName serviceName, String methodName, RequestProcessingStatus status) {
        System.out.println("publishEventForInbox Called:");
        GenericInboxEventDto event = new GenericInboxEventDto(this, id, requestDto, serviceName, methodName, status);
        applicationEventPublisher.publishEvent(event);
    }

    public void publishEventForInboxOnFailure(final String id, final Object requestDto, ServiceName serviceName, String methodName, RequestProcessingStatus status, String failureReason) {
        System.out.println("publishEventForInboxOnFailure Called:");
        GenericInboxEventDto event = new GenericInboxEventDto(this, id, requestDto, serviceName, methodName, status, failureReason);
        applicationEventPublisher.publishEvent(event);
    }
}