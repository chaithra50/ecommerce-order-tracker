package com.ordertracker.controller;

import com.ordertracker.dto.*;
import com.ordertracker.entity.User;
import com.ordertracker.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // POST /api/v1/orders — Create a new order
    @PostMapping
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
        @Valid @RequestBody OrderRequest request,
        @AuthenticationPrincipal Object currentUser) {
        
    OrderResponse response = orderService.createOrder(request, null);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Order created successfully", response));
}

    // GET /api/v1/orders/{orderNumber} — Get specific order
    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
        @PathVariable("orderNumber") String orderNumber,
        @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.getOrderByNumber(orderNumber, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // GET /api/v1/orders/my — Get current user's orders
    @GetMapping("/my")
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getMyOrders(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "10") int size,
        @AuthenticationPrincipal User currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<OrderResponse> response = orderService.getOrdersForUser(
                currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // PATCH /api/v1/orders/{orderNumber}/status — Update order status (Admin only)
    @PatchMapping("/{orderNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal User currentAdmin) {

        OrderResponse response = orderService.updateOrderStatus(orderNumber, request, currentAdmin);
        return ResponseEntity.ok(ApiResponse.success("Order status updated", response));
    }

    // DELETE /api/v1/orders/{orderNumber}/cancel — Cancel an order
    @DeleteMapping("/{orderNumber}/cancel")
public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
        @PathVariable("orderNumber") String orderNumber,
        @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.cancelOrder(orderNumber, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }
}
