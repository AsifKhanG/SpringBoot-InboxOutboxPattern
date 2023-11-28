package com.gr.common.rest.messagebox.dto;

import com.gr.common.rest.messagebox.constants.RequestProcessingStatus;
import com.gr.common.rest.messagebox.constants.ServiceName;
import org.springframework.context.ApplicationEvent;


public class GenericInboxEventDto<T> extends ApplicationEvent {

    private String id;

    private T requestDto;
    private RequestProcessingStatus status;

    private String failureReason;

    private String methodName;

    private ServiceName serviceName;



    public GenericInboxEventDto(Object source, String id, T requestDto) {
        super(source);
        this.id = id;
        this.requestDto = requestDto;
    }

    public GenericInboxEventDto(Object source, String id, T requestDto, ServiceName serviceName, String methodName, RequestProcessingStatus status) {
        super(source);
        this.id = id;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.status = status;
        this.requestDto = requestDto;
    }

    public GenericInboxEventDto(Object source, String id, T requestDto, ServiceName serviceName, String methodName, RequestProcessingStatus status, String failureReason) {
        super(source);
        this.id = id;
        this.status = status;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.requestDto = requestDto;
        this.failureReason = failureReason;
    }


    public GenericInboxEventDto(Object source, T requestDto) {
        super(source);
        this.requestDto = requestDto;
    }

    public void setRequestDto(T requestDto) {
        this.requestDto = requestDto;
    }

    public T getRequestDto() {
        return this.requestDto;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return this.failureReason;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public void setServiceName(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    public ServiceName getServiceName() {
        return this.serviceName;
    }

    public void setStatus(RequestProcessingStatus status) {
        this.status = status;
    }

    public RequestProcessingStatus getStatus() {
        return this.status;
    }

}
