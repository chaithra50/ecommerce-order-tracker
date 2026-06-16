package com.notificationservice.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String customerEmail;
    private String customerName;
    private String oldStatus;
    private String newStatus;
    private BigDecimal totalAmount;
    private String trackingNumber;
    private String statusNote;
    private String shippingAddress;
    private LocalDateTime eventTime;
    private String eventType;
}
