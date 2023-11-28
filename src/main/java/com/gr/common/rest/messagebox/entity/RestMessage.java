package com.gr.common.rest.messagebox.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;

import com.gr.common.rest.messagebox.constants.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Builder
public class RestMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Lob
    @Column(nullable = true)
    private String requestHeadersJson;

    @Lob
    private String content;

    @Lob
    private String contentType;

    private LocalDateTime createDateTime;

    @Column(nullable = false)
    private String endPointUrl;

    @Enumerated(EnumType.STRING)
    private HTTPMethod httpMethod;

    @Lob
    @Column(nullable = true)
    private String response;

    @Lob
    @Column(nullable = true)
    private String responseHeadersJson;

    @Lob
    @Column(nullable = true)
    private String queryParameters;

    @Column(nullable = true)
    private LocalDateTime responseDateTime;


    @Enumerated(EnumType.STRING)
    private RestMessageStatus status;

    @Column(nullable = true)
    private Integer reponseStatusCode;

    @Lob
    @Column(nullable = true)
    private String responseStatusMessage;

    @Column(nullable = true)
    private Integer retryCount;

    @Column(nullable = true)
    private Integer schedulerRetryCount;

    @Column(nullable = true)
    private LocalDateTime lastSendDateTime;

    @Column(nullable = true)
    private Long responseDurationInMillis;

    @Enumerated(EnumType.STRING)
    private GrSystem sourceSystem;

    @Enumerated(EnumType.STRING)
    private ServiceName sourceService;

    private String serviceMethodName;

    private String contentHash;

    @Enumerated(EnumType.STRING)
    private Type type;

}
