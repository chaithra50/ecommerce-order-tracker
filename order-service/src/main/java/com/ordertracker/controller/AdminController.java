package com.ordertracker.controller;

import com.ordertracker.dto.AnalyticsResponse;
import com.ordertracker.dto.ApiResponse;
import com.ordertracker.dto.OrderResponse;
import com.ordertracker.dto.PagedResponse;
import com.ordertracker.entity.OrderAuditLog;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.repository.OrderAuditLogRepository;
import com.ordertracker.repository.OrderRepository;
import com.ordertracker.service.OrderAnalyticsService;
import com.ordertracker.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final OrderService orderService;
    private final OrderAnalyticsService analyticsService;
    private final OrderAuditLogRepository auditLogRepository;
    private final OrderRepository orderRepository;

    // GET /api/v1/admin/orders — All orders with pagination
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getAllOrders(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<OrderResponse> response = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // GET /api/v1/admin/orders/by-status?status=SHIPPED — Filter orders by status
    @GetMapping("/orders/by-status")
public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getOrdersByStatus(
        @RequestParam(name = "status") OrderStatus status,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<OrderResponse> response = orderService.getOrdersByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // GET /api/v1/admin/analytics — Dashboard metrics snapshot
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AnalyticsResponse>> getAnalytics() {
        AnalyticsResponse response = analyticsService.getDashboardSnapshot();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // GET /api/v1/admin/orders/{orderNumber}/audit — Full status change history
    @GetMapping("/orders/{orderNumber}/audit")
public ResponseEntity<ApiResponse<java.util.List<OrderAuditLog>>> getOrderAuditLog(
        @PathVariable(name = "orderNumber") String orderNumber) {

        com.ordertracker.entity.Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new com.ordertracker.exception.ResourceNotFoundException(
                        "Order", "orderNumber", orderNumber));

        java.util.List<OrderAuditLog> log =
                auditLogRepository.findByOrderIdOrderByChangedAtDesc(order.getId());
        return ResponseEntity.ok(ApiResponse.success(log));
    }
}
