package com.notificationservice.kafka;

import com.notificationservice.model.OrderEvent;
import com.notificationservice.websocket.NotificationPayload;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class OrderEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;

    public OrderEventConsumer(SimpMessagingTemplate messagingTemplate,
                              MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.eventsProcessedCounter = Counter.builder("notification.events.processed")
                .description("Total order events processed")
                .register(meterRegistry);
        this.eventsFailedCounter = Counter.builder("notification.events.failed")
                .description("Total order events that failed processing")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderEvent(
            @Payload OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received [{}] event for order [{}] from partition={}, offset={}",
                event.getEventType(), event.getOrderNumber(), partition, offset);

        try {
            processEvent(event);
            eventsProcessedCounter.increment();
        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process event for order [{}]: {}",
                    event.getOrderNumber(), e.getMessage(), e);
            throw e; // Rethrow so Kafka retries
        }
    }

    private void processEvent(OrderEvent event) {
        NotificationPayload payload = buildPayload(event);

        // Push to order-specific topic: /topic/orders/{orderNumber}
        String orderTopic = "/topic/orders/" + event.getOrderNumber();
        messagingTemplate.convertAndSend(orderTopic, payload);
        log.info("Pushed WebSocket update to {}", orderTopic);

        // Also push to user-specific queue: /queue/users/{userId}
        String userQueue = "/queue/users/" + event.getUserId();
        messagingTemplate.convertAndSend(userQueue, payload);
        log.info("Pushed WebSocket update to {}", userQueue);
    }

    private NotificationPayload buildPayload(OrderEvent event) {
        String message = switch (event.getEventType()) {
            case "ORDER_CREATED" -> String.format(
                    "Order %s has been placed successfully! Total: ₹%.2f",
                    event.getOrderNumber(), event.getTotalAmount());
            case "ORDER_STATUS_UPDATED" -> String.format(
                    "Order %s status updated: %s → %s",
                    event.getOrderNumber(), event.getOldStatus(), event.getNewStatus());
            case "ORDER_CANCELLED" -> String.format(
                    "Order %s has been cancelled.", event.getOrderNumber());
            default -> String.format("Update for order %s: %s",
                    event.getOrderNumber(), event.getNewStatus());
        };

        return NotificationPayload.builder()
                .orderNumber(event.getOrderNumber())
                .eventType(event.getEventType())
                .oldStatus(event.getOldStatus())
                .newStatus(event.getNewStatus())
                .trackingNumber(event.getTrackingNumber())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
