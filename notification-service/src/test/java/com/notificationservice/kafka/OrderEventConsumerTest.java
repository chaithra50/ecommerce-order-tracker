package com.notificationservice.kafka;

import com.notificationservice.model.OrderEvent;
import com.notificationservice.websocket.NotificationPayload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer Unit Tests")
class OrderEventConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(messagingTemplate, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should push WebSocket update to order-specific topic on ORDER_CREATED")
    void consumeOrderEvent_OrderCreated_ShouldPushToOrderTopic() {
        OrderEvent event = buildEvent("ORDER_CREATED", null, "PENDING");

        consumer.consumeOrderEvent(event, 0, 0L);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(NotificationPayload.class);

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        // Verify order-specific topic was targeted
        assertThat(destinationCaptor.getAllValues())
                .contains("/topic/orders/ORD-WS-TEST");

        // Verify payload content
        NotificationPayload payload = payloadCaptor.getAllValues().stream()
                .filter(p -> p.getOrderNumber().equals("ORD-WS-TEST"))
                .findFirst().orElseThrow();

        assertThat(payload.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(payload.getNewStatus()).isEqualTo("PENDING");
        assertThat(payload.getMessage()).contains("ORD-WS-TEST");
        assertThat(payload.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should push to user queue on any event")
    void consumeOrderEvent_ShouldPushToUserQueue() {
        OrderEvent event = buildEvent("ORDER_STATUS_UPDATED", "CONFIRMED", "PREPARING");

        consumer.consumeOrderEvent(event, 0, 1L);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(destinationCaptor.capture(), any(NotificationPayload.class));

        assertThat(destinationCaptor.getAllValues())
                .contains("/queue/users/1");
    }

    @Test
    @DisplayName("Should include tracking number in payload when status is SHIPPED")
    void consumeOrderEvent_Shipped_ShouldIncludeTrackingNumber() {
        OrderEvent event = buildEvent("ORDER_STATUS_UPDATED", "PREPARING", "SHIPPED");
        event.setTrackingNumber("TRK-999888777");

        consumer.consumeOrderEvent(event, 0, 2L);

        ArgumentCaptor<NotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(NotificationPayload.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), payloadCaptor.capture());

        assertThat(payloadCaptor.getAllValues())
                .anyMatch(p -> "TRK-999888777".equals(p.getTrackingNumber()));
    }

    @Test
    @DisplayName("Should rethrow exception on processing failure to trigger Kafka retry")
    void consumeOrderEvent_OnException_ShouldRethrow() {
        OrderEvent event = buildEvent("ORDER_CREATED", null, "PENDING");

        doThrow(new RuntimeException("WebSocket broker down"))
                .when(messagingTemplate).convertAndSend(anyString(), any(NotificationPayload.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.consumeOrderEvent(event, 0, 3L));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private OrderEvent buildEvent(String eventType, String oldStatus, String newStatus) {
        OrderEvent event = new OrderEvent();
        event.setOrderNumber("ORD-WS-TEST");
        event.setOrderId(1L);
        event.setUserId(1L);
        event.setCustomerEmail("chaithra@test.com");
        event.setCustomerName("Chaithra Dev");
        event.setEventType(eventType);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        event.setTotalAmount(new BigDecimal("1299.00"));
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
