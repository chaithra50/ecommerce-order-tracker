package com.notificationservice.websocket;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {

    private String orderNumber;
    private String eventType;
    private String oldStatus;
    private String newStatus;
    private String trackingNumber;
    private String message;
    private LocalDateTime timestamp;
}
