package com.gr.common.rest.messagebox.aspect.inboxoutbox;

import com.gr.common.rest.messagebox.constants.RequestProcessingStatus;
import com.gr.common.rest.messagebox.entity.Inbox;
import com.gr.common.rest.messagebox.publisher.EventPublisher;
import com.gr.common.rest.messagebox.repository.InboxRepository;
import com.gr.common.v2.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.UUID;


@Aspect
@Component
@Slf4j
public class InboxAspect {

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private EventPublisher eventPublisher;

    Inbox inbox = null;

    @Before("@annotation(GRInboxPattern)")
    public void before(JoinPoint joinPoint, GRInboxPattern GRInboxPattern) {
        log.info("Inbox pattern intercepted method : {} service: {} ", GRInboxPattern.methodName(), GRInboxPattern.serviceName());

        Object[] args = joinPoint.getArgs();
        Object requestDto = args.length > 0 ? args[0] : null;
    }
    @Around("@annotation(GRInboxPattern)")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object around(ProceedingJoinPoint proceedingJoinPoint, GRInboxPattern GRInboxPattern) throws Throwable {
        log.info("Inbox pattern intercepted method : {} service: {} ", GRInboxPattern.methodName(), GRInboxPattern.serviceName());

        Object[] args = proceedingJoinPoint.getArgs();
        Object requestDto = args.length > 0 ? args[0] : null;
        // Generate a random UUID
        log.info("Generating random UUID");
        UUID randomUUID = UUID.randomUUID();
        // Convert the UUID to a string
        String randomUUIDStr = randomUUID.toString();
        // Print the random UUID as a string
        System.out.println("random UUID: " + randomUUIDStr);


        try {
            eventPublisher.publishEventForInbox(randomUUIDStr, requestDto, GRInboxPattern.serviceName(), GRInboxPattern.methodName(), RequestProcessingStatus.PROCESSING);
            // Proceed with the method execution
//            inbox = createRestMessage(randomUUIDStr, requestDto);
//            inbox = inboxRepository.save(inbox);
//            log.info("saved to inbox with id:" + inbox.getId());
            Object result = proceedingJoinPoint.proceed();
//            log.info("marked inbox with id:" + inbox.getId() + " as success");
            eventPublisher.publishEventForInbox(randomUUIDStr, requestDto, GRInboxPattern.serviceName(), GRInboxPattern.methodName(), RequestProcessingStatus.SUCCESS);

//            inbox.setStatus(Inbox.Status.SUCCESS);
//            inboxRepository.save(inbox);
            // If the method execution is successful, return the result
            return result;
        } catch (Throwable throwable) {
            log.info("Inbox pattern intercepted failure while procession method : {} service {}", GRInboxPattern.methodName(), GRInboxPattern.serviceName());

            if (Util.isNotNull(throwable.getLocalizedMessage())){
                eventPublisher.publishEventForInboxOnFailure(randomUUIDStr, requestDto, GRInboxPattern.serviceName(), GRInboxPattern.methodName(), RequestProcessingStatus.FAILURE, throwable.getLocalizedMessage());
            } else if (Util.isNull(throwable.getLocalizedMessage()) && Util.isNotNull(throwable.getMessage())) {
                eventPublisher.publishEventForInboxOnFailure(randomUUIDStr, requestDto, GRInboxPattern.serviceName(), GRInboxPattern.methodName(), RequestProcessingStatus.FAILURE, throwable.getMessage());
            } else {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                String sStackTrace = sw.toString(); // stack trace as a string
                eventPublisher.publishEventForInboxOnFailure(randomUUIDStr, requestDto, GRInboxPattern.serviceName(), GRInboxPattern.methodName(), RequestProcessingStatus.FAILURE, sStackTrace);

            }
//            inbox.setReason(throwable.getLocalizedMessage());
//            inbox.setStatus(RequestProcessingStatus.FAILURE);
//            inboxRepository.save(inbox);
            // Rethrow the exception
            throw throwable;
        }
    }

}
