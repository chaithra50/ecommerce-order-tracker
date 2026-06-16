package com.ordertracker.repository;

import com.ordertracker.entity.Order;
import com.ordertracker.entity.OrderItem;
import com.ordertracker.entity.User;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OrderRepository Integration Tests")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private User customer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();

        customer = userRepository.save(User.builder()
                .email("repo-test@example.com")
                .password("encodedPass")
                .fullName("Repo Tester")
                .phone("+919999999999")
                .role(Role.ROLE_CUSTOMER)
                .build());
    }

    @Test
    @DisplayName("Should save and retrieve order by order number")
    void save_ThenFindByOrderNumber_ShouldReturnOrder() {
        Order order = buildOrder("ORD-REPO-001", OrderStatus.PENDING);
        orderRepository.save(order);

        Optional<Order> found = orderRepository.findByOrderNumber("ORD-REPO-001");

        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo("199.99");
        assertThat(found.get().getItems()).hasSize(1);
    }

    @Test
    @DisplayName("Should return empty when order number does not exist")
    void findByOrderNumber_WhenNotExists_ShouldReturnEmpty() {
        Optional<Order> found = orderRepository.findByOrderNumber("ORD-NONEXISTENT");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find orders by user ID with pagination, newest first")
    void findByUserId_ShouldReturnPagedOrdersNewestFirst() {
        orderRepository.save(buildOrder("ORD-USER-001", OrderStatus.PENDING));
        orderRepository.save(buildOrder("ORD-USER-002", OrderStatus.CONFIRMED));
        orderRepository.save(buildOrder("ORD-USER-003", OrderStatus.SHIPPED));

        Page<Order> page = orderRepository.findByUserIdOrderByCreatedAtDesc(
                customer.getId(), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should count orders by status")
    void countByStatus_ShouldReturnCorrectCount() {
        orderRepository.save(buildOrder("ORD-CNT-001", OrderStatus.PENDING));
        orderRepository.save(buildOrder("ORD-CNT-002", OrderStatus.PENDING));
        orderRepository.save(buildOrder("ORD-CNT-003", OrderStatus.CONFIRMED));

        long pendingCount = orderRepository.countByStatus(OrderStatus.PENDING);
        long confirmedCount = orderRepository.countByStatus(OrderStatus.CONFIRMED);

        assertThat(pendingCount).isEqualTo(2);
        assertThat(confirmedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find orders by user ID and status")
    void findByUserIdAndStatus_ShouldFilterCorrectly() {
        orderRepository.save(buildOrder("ORD-STAT-001", OrderStatus.PENDING));
        orderRepository.save(buildOrder("ORD-STAT-002", OrderStatus.PENDING));
        orderRepository.save(buildOrder("ORD-STAT-003", OrderStatus.DELIVERED));

        List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(
                customer.getId(), OrderStatus.PENDING);

        assertThat(pendingOrders).hasSize(2);
        assertThat(pendingOrders)
                .extracting(Order::getStatus)
                .containsOnly(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should cascade delete order items when order is deleted")
    void delete_ShouldCascadeToOrderItems() {
        Order order = buildOrder("ORD-CASCADE-001", OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        orderRepository.deleteById(saved.getId());

        assertThat(orderRepository.findByOrderNumber("ORD-CASCADE-001")).isEmpty();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Order buildOrder(String orderNumber, OrderStatus status) {
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(customer)
                .status(status)
                .totalAmount(new BigDecimal("199.99"))
                .shippingAddress("456 Test Street, Mysuru")
                .build();

        OrderItem item = OrderItem.builder()
                .productName("Wireless Mouse")
                .productSku("WM-001")
                .quantity(1)
                .unitPrice(new BigDecimal("199.99"))
                .subtotal(new BigDecimal("199.99"))
                .build();

        order.addItem(item);
        return order;
    }
}
