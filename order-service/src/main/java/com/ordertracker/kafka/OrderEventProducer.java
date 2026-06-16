package com.ordertracker.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topics.order-events}")
    private String orderEventsTopic;

    public void publishOrderEvent(OrderEvent event) {
        // Use orderNumber as key so all events for the same order go to same partition
        String key = event.getOrderNumber();

        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(orderEventsTopic, key, event);

        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to publish order event for order [{}]: {}",
                        event.getOrderNumber(), exception.getMessage());
            } else {
                log.info("Published [{}] event for order [{}] → partition={}, offset={}",
                        event.getEventType(),
                        event.getOrderNumber(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
