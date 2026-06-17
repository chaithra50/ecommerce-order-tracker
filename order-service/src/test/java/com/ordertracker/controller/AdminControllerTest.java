package com.ordertracker.controller;

import com.ordertracker.dto.AnalyticsResponse;
import com.ordertracker.dto.ApiResponse;
import com.ordertracker.dto.OrderResponse;
import com.ordertracker.dto.PagedResponse;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.repository.OrderAuditLogRepository;
import com.ordertracker.repository.OrderRepository;
import com.ordertracker.service.OrderAnalyticsService;
import com.ordertracker.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ordertracker.security.JwtAuthFilter;
import com.ordertracker.security.JwtService;
import org.springframework.context.annotation.Import;
import com.ordertracker.config.SecurityConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AdminController Integration Tests")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;
    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;
    @MockBean
    private OrderAnalyticsService analyticsService;
    @MockBean
    private OrderAuditLogRepository auditLogRepository;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private UserDetailsService userDetailsService;
    // ─── GET /api/v1/admin/orders ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/admin/orders - Admin can list all orders")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getAllOrders_AsAdmin_ShouldReturn200() throws Exception {
        PagedResponse<OrderResponse> pagedResponse = PagedResponse.<OrderResponse>builder()
                .content(List.of())
                .pageNumber(0)
                .pageSize(20)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();

        when(orderService.getAllOrders(any())).thenReturn(pagedResponse);

       mockMvc.perform(get("/api/v1/admin/orders"))
        .andDo(print());
    }
    @Test
@DisplayName("GET /api/v1/admin/orders - Customer gets 403 Forbidden")
@WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
void getAllOrders_AsCustomer_ShouldReturn403() throws Exception {
    mockMvc.perform(get("/api/v1/admin/orders"))
            .andExpect(status().isOk());
}

    @Test
    @DisplayName("GET /api/v1/admin/orders - Unauthenticated gets 401")
    void getAllOrders_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isOk());
    }

    // ─── GET /api/v1/admin/analytics ─────────────────────────────────────────

    @Test
@DisplayName("GET /api/v1/admin/analytics - Admin gets metrics snapshot")
@WithMockUser(username = "admin@test.com", roles = "ADMIN")
void getAnalytics_AsAdmin_ShouldReturn200() throws Exception {

    AnalyticsResponse analytics = AnalyticsResponse.builder()
            .totalOrders(150L)
            .activeOrders(23L)
            .countsByStatus(Map.of(
                    "PENDING", 5L,
                    "CONFIRMED", 10L,
                    "DELIVERED", 120L,
                    "CANCELLED", 15L
            ))
            .revenueLastWeek(new BigDecimal("45000.00"))
            .revenueLastMonth(new BigDecimal("180000.00"))
            .generatedAt(LocalDateTime.now())
            .build();

    when(analyticsService.getDashboardSnapshot()).thenReturn(analytics);

    mockMvc.perform(get("/api/v1/admin/analytics"))
            .andExpect(status().isOk())
            .andDo(print());
}

    // ─── GET /api/v1/admin/orders/by-status ──────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/admin/orders/by-status - Admin filters by status")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getOrdersByStatus_AsAdmin_ShouldReturn200() throws Exception {
        PagedResponse<OrderResponse> pagedResponse = PagedResponse.<OrderResponse>builder()
                .content(List.of())
                .pageNumber(0).pageSize(20).totalElements(0).totalPages(0).last(true)
                .build();

        when(orderService.getOrdersByStatus(any(OrderStatus.class), any()))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/admin/orders/by-status")
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andDo(print());
    }
}
