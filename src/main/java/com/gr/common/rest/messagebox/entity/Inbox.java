package com.gr.common.rest.messagebox.entity;

import com.gr.common.rest.messagebox.constants.RequestProcessingStatus;
import com.gr.common.rest.messagebox.constants.ServiceName;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Builder
public class Inbox {
    @Id
    private String id;

    @Lob
    private String content;

    @Lob
    private String contentType;

    private LocalDateTime createDateTime;

    @Enumerated(EnumType.STRING)
    private RequestProcessingStatus status;

    @Enumerated(EnumType.STRING)
    private ServiceName serviceName;

    private String methodName;

    @Lob
    private String reason;


}
