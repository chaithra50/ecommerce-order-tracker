package com.ordertracker.kafka;

import com.ordertracker.enums.OrderStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"test-order-events"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9099",
                "port=9099"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "kafka.topics.order-events=test-order-events"
})
@DisplayName("OrderEventProducer Kafka Integration Tests")
class OrderEventProducerIntegrationTest {

    @Autowired
    private OrderEventProducer eventProducer;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaMessageListenerContainer<String, OrderEvent> container;
    private BlockingQueue<ConsumerRecord<String, OrderEvent>> receivedRecords;

    @BeforeEach
    void setUp() {
        receivedRecords = new LinkedBlockingQueue<>();

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafkaBroker.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<OrderEvent> deserializer = new JsonDeserializer<>(OrderEvent.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, OrderEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps,
                        new StringDeserializer(), deserializer);

        ContainerProperties containerProperties =
                new ContainerProperties("test-order-events");

        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener(
                (MessageListener<String, OrderEvent>) receivedRecords::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container,
                embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        container.stop();
    }

    @Test
    @DisplayName("Should publish ORDER_CREATED event to Kafka topic")
    void publishOrderEvent_ShouldBeReceivableFromTopic() throws InterruptedException {
        OrderEvent event = OrderEvent.builder()
                .orderId(1L)
                .orderNumber("ORD-KAFKA-TEST")
                .userId(1L)
                .customerEmail("chaithra@test.com")
                .customerName("Chaithra Dev")
                .newStatus(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("999.00"))
                .eventTime(LocalDateTime.now())
                .eventType(OrderEvent.EventType.ORDER_CREATED)
                .build();

        eventProducer.publishOrderEvent(event);

        // Wait up to 10 seconds for the message to arrive
        ConsumerRecord<String, OrderEvent> received =
                receivedRecords.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo("ORD-KAFKA-TEST");
        assertThat(received.value().getOrderNumber()).isEqualTo("ORD-KAFKA-TEST");
        assertThat(received.value().getEventType())
                .isEqualTo(OrderEvent.EventType.ORDER_CREATED);
        assertThat(received.value().getCustomerEmail()).isEqualTo("chaithra@test.com");
    }

    @Test
    @DisplayName("Should use orderNumber as Kafka partition key")
    void publishOrderEvent_ShouldUseOrderNumberAsKey() throws InterruptedException {
        OrderEvent event = OrderEvent.builder()
                .orderId(2L)
                .orderNumber("ORD-KEY-TEST")
                .userId(1L)
                .customerEmail("chaithra@test.com")
                .customerName("Chaithra Dev")
                .oldStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("500.00"))
                .eventTime(LocalDateTime.now())
                .eventType(OrderEvent.EventType.ORDER_STATUS_UPDATED)
                .build();

        eventProducer.publishOrderEvent(event);

        ConsumerRecord<String, OrderEvent> received =
                receivedRecords.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        // Key ensures all events for same order go to same partition (ordering guarantee)
        assertThat(received.key()).isEqualTo("ORD-KEY-TEST");
    }
}
