package com.gr.common.rest.messagebox.listener;

import com.gr.common.rest.messagebox.constants.RequestProcessingStatus;
import com.gr.common.rest.messagebox.dto.GenericInboxEventDto;
import com.gr.common.rest.messagebox.entity.Inbox;
import com.gr.common.rest.messagebox.repository.InboxRepository;
import com.gr.common.v2.util.Util;
import com.gr.grid.common.util.JsonUtil;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GenericSpringEventListener
        implements ApplicationListener<GenericInboxEventDto<Object>> {

    @Autowired
    private InboxRepository inboxRepository;

    Inbox inbox = null;

    @Async
    @Override
    public void onApplicationEvent(@NonNull GenericInboxEventDto<Object> event) {
        System.out.println("Received event UUID - " + event.getId());
        System.out.println("Received event request - " + event.getRequestDto());
        System.out.println("Received event status - " + event.getStatus());
        // Check if the status is success then delete the record from inbox table
        if (RequestProcessingStatus.SUCCESS.equals(event.getStatus())){
            System.out.println("Deleting from inbox on success...");
            inboxRepository.deleteById(event.getId());
        }else{
            System.out.println("saving in inbox...");
            inbox = inboxRepository.save(createRestMessage(event, event.getStatus(), event.getFailureReason()));
        }
    }

    private Inbox createRestMessage(GenericInboxEventDto<Object> eventDto, RequestProcessingStatus status, String failureReason) {
        return Inbox.builder()
                .id(eventDto.getId())
                .content(JsonUtil.convertToJson(eventDto.getRequestDto()))
                .contentType("JSON")
                .createDateTime(LocalDateTime.now())
                .serviceName(eventDto.getServiceName())
                .methodName(eventDto.getMethodName())
                .status(status)
                .reason(Util.isNotNullAndEmpty(failureReason) ? failureReason : null)
                .build();
    }
}