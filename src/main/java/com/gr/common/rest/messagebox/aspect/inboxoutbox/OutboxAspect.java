package com.gr.common.rest.messagebox.aspect.inboxoutbox;

import com.gr.common.rest.messagebox.constants.RestMessageStatus;
import com.gr.common.rest.messagebox.constants.Type;
import com.gr.common.rest.messagebox.entity.RestMessage;
import com.gr.common.rest.messagebox.service.RestMessageService;
import com.gr.common.v2.exception.ApiException;
import com.gr.grid.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
public class OutboxAspect {

    @Autowired
    private RestMessageService restMessageService;

    @Around("@annotation(GROutboxPattern)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint, GROutboxPattern GROutboxPattern) throws Throwable {
        Object[] args = proceedingJoinPoint.getArgs();
        Object requestDto = args.length > 0 ? args[0] : null;

        try {
            // Proceed with the method execution
            Object result = proceedingJoinPoint.proceed();

            // If the method execution is successful, return the result
            return result;
        } catch (Throwable throwable) {
            // Check if the exception is not ApiException
            if (!(throwable instanceof ApiException)) {
                // Store the input in a RestMessage
                RestMessage restMessage = createRestMessage(GROutboxPattern, requestDto);
                restMessageService.saveRestMessage(restMessage);
            }

            // Rethrow the exception
            throw throwable;
        }
    }

    private RestMessage createRestMessage(GROutboxPattern GROutboxPattern, Object requestDto) {
        // Create a RestMessage object with the necessary details
        // Set the content, endpoint, method name, etc.
        // Customize it based on your requirements

        return RestMessage.builder()
                .content(JsonUtil.toJson(requestDto))
                .contentType("JSON")
                .type(Type.OUTBOX)
                .createDateTime(LocalDateTime.now())
                .status(RestMessageStatus.FAILURE)
                .build();
    }
}
