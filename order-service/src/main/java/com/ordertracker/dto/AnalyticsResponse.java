package com.ordertracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private long totalOrders;
    private long activeOrders;
    private Map<String, Long> countsByStatus;
    private BigDecimal revenueLastWeek;
    private BigDecimal revenueLastMonth;
    private LocalDateTime generatedAt;
}
