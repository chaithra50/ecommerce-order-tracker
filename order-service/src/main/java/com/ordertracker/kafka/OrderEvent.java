package com.ordertracker.kafka;

import com.ordertracker.enums.OrderStatus;
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
    private OrderStatus oldStatus;
    private OrderStatus newStatus;
    private BigDecimal totalAmount;
    private String trackingNumber;
    private String statusNote;
    private String shippingAddress;
    private LocalDateTime eventTime;
    private EventType eventType;

    public enum EventType {
        ORDER_CREATED,
        ORDER_STATUS_UPDATED,
        ORDER_CANCELLED
    }
}
