package com.ordertracker.service;

import com.ordertracker.dto.OrderRequest;
import com.ordertracker.dto.OrderResponse;
import com.ordertracker.dto.OrderStatusUpdateRequest;
import com.ordertracker.entity.Order;
import com.ordertracker.entity.User;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.enums.Role;
import com.ordertracker.exception.InvalidOrderStateException;
import com.ordertracker.exception.ResourceNotFoundException;
import com.ordertracker.kafka.OrderEvent;
import com.ordertracker.kafka.OrderEventProducer;
import com.ordertracker.repository.OrderAuditLogRepository;
import com.ordertracker.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderAuditLogRepository auditLogRepository;

    @Mock
    private OrderEventProducer eventProducer;

    private OrderService orderService;

    private User testCustomer;
    private User testAdmin;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, auditLogRepository, eventProducer, new SimpleMeterRegistry());

        testCustomer = User.builder()
                .id(1L)
                .email("customer@test.com")
                .fullName("Test Customer")
                .phone("+1234567890")
                .role(Role.ROLE_CUSTOMER)
                .password("encoded-password")
                .build();

        testAdmin = User.builder()
                .id(2L)
                .email("admin@test.com")
                .fullName("Test Admin")
                .phone("+9876543210")
                .role(Role.ROLE_ADMIN)
                .password("encoded-password")
                .build();
    }

    @Test
    @DisplayName("Should create order successfully with correct total calculation")
    void createOrder_ShouldCalculateTotalCorrectly() {
        // Arrange
        OrderRequest request = buildOrderRequest();

        Order savedOrder = buildOrder(testCustomer, OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.createOrder(request, testCustomer);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getCustomerEmail()).isEqualTo(testCustomer.getEmail());

        // Verify Kafka event was published
        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventProducer, times(1)).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(OrderEvent.EventType.ORDER_CREATED);

        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when order not found")
    void getOrderByNumber_WhenNotFound_ShouldThrowException() {
        // Arrange
        when(orderRepository.findByOrderNumber("INVALID-123"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderByNumber("INVALID-123", testCustomer))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order");

        verifyNoInteractions(eventProducer);
    }

    @Test
    @DisplayName("Should prevent customer from viewing another customer's order")
    void getOrderByNumber_WhenOtherCustomerOrder_ShouldThrowException() {
        // Arrange
        User otherCustomer = User.builder()
                .id(99L)
                .email("other@test.com")
                .role(Role.ROLE_CUSTOMER)
                .password("pass")
                .build();

        Order order = buildOrder(otherCustomer, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderByNumber(
                order.getOrderNumber(), testCustomer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should allow admin to view any order")
    void getOrderByNumber_WhenAdmin_ShouldReturnOrder() {
        // Arrange
        Order order = buildOrder(testCustomer, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        // Act
        OrderResponse response = orderService.getOrderByNumber(order.getOrderNumber(), testAdmin);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrderNumber()).isEqualTo(order.getOrderNumber());
    }

    @Test
    @DisplayName("Should update status following valid transition")
    void updateOrderStatus_ValidTransition_ShouldUpdateAndPublishEvent() {
        // Arrange
        Order order = buildOrder(testCustomer, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(auditLogRepository.save(any())).thenReturn(null);

        OrderStatusUpdateRequest request = OrderStatusUpdateRequest.builder()
                .status(OrderStatus.CONFIRMED)
                .statusNote("Payment verified")
                .build();

        // Act
        OrderResponse response = orderService.updateOrderStatus(
                order.getOrderNumber(), request, testAdmin);

        // Assert
        assertThat(response).isNotNull();
        verify(orderRepository).save(any(Order.class));

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventProducer).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType())
                .isEqualTo(OrderEvent.EventType.ORDER_STATUS_UPDATED);
        assertThat(eventCaptor.getValue().getOldStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(eventCaptor.getValue().getNewStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Should reject invalid status transition")
    void updateOrderStatus_InvalidTransition_ShouldThrowException() {
        // Arrange
        Order order = buildOrder(testCustomer, OrderStatus.DELIVERED);
        when(orderRepository.findByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        OrderStatusUpdateRequest request = OrderStatusUpdateRequest.builder()
                .status(OrderStatus.PENDING) // Cannot go backward
                .build();

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateOrderStatus(
                order.getOrderNumber(), request, testAdmin))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot transition");

        verifyNoInteractions(eventProducer);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel order when in cancellable state")
    void cancelOrder_WhenCancellable_ShouldCancel() {
        // Arrange
        Order order = buildOrder(testCustomer, OrderStatus.PENDING);
        order.setUser(testCustomer);
        when(orderRepository.findByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(auditLogRepository.save(any())).thenReturn(null);

        // Act
        OrderResponse response = orderService.cancelOrder(order.getOrderNumber(), testCustomer);

        // Assert
        assertThat(response).isNotNull();
        verify(eventProducer).publishOrderEvent(
                argThat(e -> e.getEventType() == OrderEvent.EventType.ORDER_CANCELLED));
    }

    @Test
    @DisplayName("Should not cancel delivered order")
    void cancelOrder_WhenDelivered_ShouldThrowException() {
        // Arrange
        Order order = buildOrder(testCustomer, OrderStatus.DELIVERED);
        when(orderRepository.findByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancelOrder(order.getOrderNumber(), testAdmin))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(eventProducer);
    }

    // ─── Test Helpers ─────────────────────────────────────────────────────────

    private OrderRequest buildOrderRequest() {
        OrderRequest.OrderItemRequest item = OrderRequest.OrderItemRequest.builder()
                .productName("Gaming Keyboard")
                .productSku("KB-001")
                .quantity(2)
                .unitPrice(new BigDecimal("49.99"))
                .build();

        return OrderRequest.builder()
                .shippingAddress("123 Main St, Bangalore, KA 560001")
                .items(List.of(item))
                .build();
    }

    private Order buildOrder(User user, OrderStatus status) {
        Order order = Order.builder()
                .id(1L)
                .orderNumber("ORD-TEST001")
                .user(user)
                .status(status)
                .totalAmount(new BigDecimal("99.98"))
                .shippingAddress("123 Main St")
                .items(new ArrayList<>())
                .build();
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
