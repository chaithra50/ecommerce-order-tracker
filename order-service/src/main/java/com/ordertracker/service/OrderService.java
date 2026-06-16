package com.ordertracker.service;

import com.ordertracker.dto.*;
import com.ordertracker.entity.Order;
import com.ordertracker.entity.OrderAuditLog;
import com.ordertracker.entity.OrderItem;
import com.ordertracker.entity.User;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.exception.InvalidOrderStateException;
import com.ordertracker.exception.ResourceNotFoundException;
import com.ordertracker.kafka.OrderEvent;
import com.ordertracker.kafka.OrderEventProducer;
import com.ordertracker.repository.OrderAuditLogRepository;
import com.ordertracker.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderAuditLogRepository auditLogRepository;
    private final OrderEventProducer eventProducer;
    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final Timer orderCreationTimer;

    // Valid status transitions
    private static final java.util.Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS =
            java.util.Map.of(
                    OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                    OrderStatus.CONFIRMED, Set.of(OrderStatus.PAYMENT_PROCESSING, OrderStatus.CANCELLED),
                    OrderStatus.PAYMENT_PROCESSING, Set.of(OrderStatus.PREPARING, OrderStatus.PAYMENT_FAILED),
                    OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.PAYMENT_PROCESSING, OrderStatus.CANCELLED),
                    OrderStatus.PREPARING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
                    OrderStatus.SHIPPED, Set.of(OrderStatus.OUT_FOR_DELIVERY),
                    OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
                    OrderStatus.DELIVERED, Set.of(OrderStatus.REFUNDED),
                    OrderStatus.CANCELLED, Set.of(),
                    OrderStatus.REFUNDED, Set.of()
            );

    public OrderService(OrderRepository orderRepository,
                        OrderAuditLogRepository auditLogRepository,
                        OrderEventProducer eventProducer,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventProducer = eventProducer;
        this.ordersCreatedCounter = Counter.builder("orders.created")
                .description("Total number of orders created")
                .register(meterRegistry);
        this.ordersCancelledCounter = Counter.builder("orders.cancelled")
                .description("Total number of orders cancelled")
                .register(meterRegistry);
        this.orderCreationTimer = Timer.builder("orders.creation.time")
                .description("Time taken to create an order")
                .register(meterRegistry);
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request, User user) {
        return orderCreationTimer.record(() -> {
            log.info("Creating order for user: {}", user.getEmail());

            Order order = Order.builder()
                    .orderNumber(generateOrderNumber())
                    .user(user)
                    .status(OrderStatus.PENDING)
                    .shippingAddress(request.getShippingAddress())
                    .build();

            BigDecimal total = BigDecimal.ZERO;
            for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
                BigDecimal subtotal = itemReq.getUnitPrice()
                        .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

                OrderItem item = OrderItem.builder()
                        .productName(itemReq.getProductName())
                        .productSku(itemReq.getProductSku())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .subtotal(subtotal)
                        .build();

                order.addItem(item);
                total = total.add(subtotal);
            }

            order.setTotalAmount(total);
            Order savedOrder = orderRepository.save(order);

            // Publish Kafka event
            eventProducer.publishOrderEvent(buildEvent(savedOrder, null,
                    OrderEvent.EventType.ORDER_CREATED));

            ordersCreatedCounter.increment();
            log.info("Order [{}] created successfully", savedOrder.getOrderNumber());

            return mapToResponse(savedOrder);
        });
    }
@Cacheable(value = "orders", key = "#p0")
@Transactional(readOnly = true)
public OrderResponse getOrderByNumber(String orderNumber, User requestingUser) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        // Customers can only view their own orders
        if (!isAdmin(requestingUser) && !order.getUser().getId().equals(requestingUser.getId())) {
            throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
        }

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersForUser(Long userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Page<OrderResponse> responsePage = orders.map(this::mapToResponse);
        return PagedResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        Page<OrderResponse> responsePage = orders.map(this::mapToResponse);
        return PagedResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        Page<OrderResponse> responsePage = orders.map(this::mapToResponse);
        return PagedResponse.from(responsePage);
    }

    @Transactional
    @CacheEvict(value = "orders", key = "#orderNumber")
    public OrderResponse updateOrderStatus(String orderNumber,
                                           OrderStatusUpdateRequest request,
                                           User admin) {
        log.info("Admin [{}] updating order [{}] to status [{}]",
                admin.getEmail(), orderNumber, request.getStatus());

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        OrderStatus oldStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        // Validate state transition
        Set<OrderStatus> allowed = VALID_TRANSITIONS.get(oldStatus);
        if (!allowed.contains(newStatus)) {
            throw new InvalidOrderStateException(
                    String.format("Cannot transition order from [%s] to [%s]", oldStatus, newStatus));
        }

        order.setStatus(newStatus);
        if (request.getTrackingNumber() != null) {
            order.setTrackingNumber(request.getTrackingNumber());
        }
        if (request.getStatusNote() != null) {
            order.setStatusNote(request.getStatusNote());
        }

        Order updatedOrder = orderRepository.save(order);

        // Write immutable audit trail
        writeAuditLog(updatedOrder, oldStatus, newStatus, admin.getEmail(), request.getStatusNote());

        if (newStatus == OrderStatus.CANCELLED) {
            ordersCancelledCounter.increment();
        }

        // Publish status-change event to Kafka
        eventProducer.publishOrderEvent(buildEvent(updatedOrder, oldStatus,
                OrderEvent.EventType.ORDER_STATUS_UPDATED));

        log.info("Order [{}] status updated: {} → {}", orderNumber, oldStatus, newStatus);
        return mapToResponse(updatedOrder);
    }

    @Transactional
@CacheEvict(value = "orders", key = "#p0")
public OrderResponse cancelOrder(String orderNumber, User user) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (!isAdmin(user) && !order.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
        }

        Set<OrderStatus> allowed = VALID_TRANSITIONS.get(order.getStatus());
        if (!allowed.contains(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException(
                    String.format("Order in status [%s] cannot be cancelled", order.getStatus()));
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        String cancelNote = "Cancelled by " + (isAdmin(user) ? "admin" : "customer");
        order.setStatusNote(cancelNote);
        Order saved = orderRepository.save(order);

        // Write immutable audit trail
        writeAuditLog(saved, oldStatus, OrderStatus.CANCELLED, user.getEmail(), cancelNote);

        ordersCancelledCounter.increment();
        eventProducer.publishOrderEvent(buildEvent(saved, oldStatus,
                OrderEvent.EventType.ORDER_CANCELLED));

        log.info("Order [{}] cancelled by user [{}]", orderNumber, user.getEmail());
        return mapToResponse(saved);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void writeAuditLog(Order order, OrderStatus oldStatus,
                                OrderStatus newStatus, String changedBy, String note) {
        OrderAuditLog log = OrderAuditLog.builder()
                .order(order)
                .oldStatus(oldStatus != null ? oldStatus.name() : null)
                .newStatus(newStatus.name())
                .changedBy(changedBy)
                .note(note)
                .build();
        auditLogRepository.save(log);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private OrderEvent buildEvent(Order order, OrderStatus oldStatus, OrderEvent.EventType type) {
        return OrderEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .customerEmail(order.getUser().getEmail())
                .customerName(order.getUser().getFullName())
                .oldStatus(oldStatus)
                .newStatus(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .trackingNumber(order.getTrackingNumber())
                .statusNote(order.getStatusNote())
                .shippingAddress(order.getShippingAddress())
                .eventTime(LocalDateTime.now())
                .eventType(type)
                .build();
    }

    public OrderResponse mapToResponse(Order order) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .id(item.getId())
                        .productName(item.getProductName())
                        .productSku(item.getProductSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .customerEmail(order.getUser().getEmail())
                .items(items)
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .trackingNumber(order.getTrackingNumber())
                .statusNote(order.getStatusNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
