package com.ordertracker.service;

import com.ordertracker.dto.AnalyticsResponse;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAnalyticsService {

    private final OrderRepository orderRepository;

    /**
     * Returns a snapshot of key business metrics.
     * Cached for 60 seconds — admin dashboards call this on page load.
     */
    @Cacheable(value = "analytics", key = "'dashboard-snapshot'")
    @Transactional(readOnly = true)
    public AnalyticsResponse getDashboardSnapshot() {
        log.info("Computing analytics dashboard snapshot");

        // Order counts by status
        Map<String, Long> countsByStatus = new LinkedHashMap<>();
        Arrays.stream(OrderStatus.values()).forEach(status ->
                countsByStatus.put(status.name(), orderRepository.countByStatus(status)));

        // Revenue in the last 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        BigDecimal revenueLastWeek = orderRepository.getTotalRevenueAfter(sevenDaysAgo);

        // Revenue in the last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        BigDecimal revenueLastMonth = orderRepository.getTotalRevenueAfter(thirtyDaysAgo);

        // Total orders
        long totalOrders = orderRepository.count();

        // Active orders (not terminal states)
        long activeOrders = countsByStatus.getOrDefault("PENDING", 0L)
                + countsByStatus.getOrDefault("CONFIRMED", 0L)
                + countsByStatus.getOrDefault("PAYMENT_PROCESSING", 0L)
                + countsByStatus.getOrDefault("PREPARING", 0L)
                + countsByStatus.getOrDefault("SHIPPED", 0L)
                + countsByStatus.getOrDefault("OUT_FOR_DELIVERY", 0L);

        return AnalyticsResponse.builder()
                .totalOrders(totalOrders)
                .activeOrders(activeOrders)
                .countsByStatus(countsByStatus)
                .revenueLastWeek(revenueLastWeek != null ? revenueLastWeek : BigDecimal.ZERO)
                .revenueLastMonth(revenueLastMonth != null ? revenueLastMonth : BigDecimal.ZERO)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
