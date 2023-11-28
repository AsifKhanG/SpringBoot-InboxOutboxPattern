package com.gr.common.rest.messagebox.aspect.inboxoutbox;

import com.gr.common.rest.messagebox.constants.ServiceName;
import com.gr.common.rest.messagebox.entity.RestMessage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GRInboxPattern {
    ServiceName serviceName();
    String methodName();
}
