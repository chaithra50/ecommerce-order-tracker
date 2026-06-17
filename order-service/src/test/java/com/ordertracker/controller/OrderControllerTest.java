package com.ordertracker.controller;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordertracker.dto.OrderRequest;
import com.ordertracker.dto.OrderResponse;
import com.ordertracker.entity.User;
import com.ordertracker.enums.OrderStatus;
import com.ordertracker.enums.Role;
import com.ordertracker.service.OrderService;
import com.ordertracker.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


import org.springframework.security.core.userdetails.UserDetailsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ordertracker.security.JwtAuthFilter;
import com.ordertracker.security.JwtService;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("OrderController Integration Tests")
class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

//     @Autowired
// private RequestMappingHandlerMapping mapping;

// @Test
// void checkPostMapping() {
//     mapping.getHandlerMethods().forEach((info, method) -> {
//         if (info.toString().contains("/api/v1/orders")) {
//             System.out.println(info + " -> " + method);
//         }
//     });
// }


    @MockBean
    private OrderService orderService;
    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;
   
    @MockBean
    private UserDetailsService userDetailsService;
     private OrderResponse sampleOrderResponse;
    @BeforeEach
    void setUp() {
        sampleOrderResponse = OrderResponse.builder()
                .id(1L)
                .orderNumber("ORD-ABC12345")
                .userId(1L)
                .customerEmail("customer@test.com")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("99.98"))
                .shippingAddress("123 Main St, Bangalore")
                .items(List.of(
                        OrderResponse.OrderItemResponse.builder()
                                .id(1L)
                                .productName("Gaming Keyboard")
                                .productSku("KB-001")
                                .quantity(2)
                                .unitPrice(new BigDecimal("49.99"))
                                .subtotal(new BigDecimal("99.98"))
                                .build()
                ))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    

    @Test
@DisplayName("POST /api/v1/orders - Should create order for authenticated user")
@WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
void createOrder_WithValidRequest_ShouldReturn201() throws Exception {

    OrderRequest request = OrderRequest.builder()
            .shippingAddress("123 Main St, Bangalore, KA 560001")
            .items(List.of(
                    OrderRequest.OrderItemRequest.builder()
                            .productName("Gaming Keyboard")
                            .productSku("KB-001")
                            .quantity(2)
                            .unitPrice(new BigDecimal("49.99"))
                            .build()
            ))
            .build();

    when(orderService.createOrder(any(), any()))
            .thenReturn(sampleOrderResponse);
        

    mockMvc.perform(post("/api/v1/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isCreated());
}

   @Test
@DisplayName("GET /api/v1/orders/{orderNumber} - Should return order for authenticated user")
@WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
void getOrder_WithValidOrderNumber_ShouldReturn200() throws Exception {

    when(orderService.getOrderByNumber(any(), any()))
            .thenReturn(sampleOrderResponse);

    mockMvc.perform(get("/api/v1/orders/ORD-ABC12345"))
            .andExpect(status().isOk());
}

    


//     @Test
//     @DisplayName("PATCH /api/v1/orders/{orderNumber}/status - Should return 403 for customer")
//     @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
//     void updateOrderStatus_AsCustomer_ShouldReturn403() throws Exception {
//         mockMvc.perform(patch("/api/v1/orders/ORD-ABC12345/status")
//                         .with(csrf())
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content("{\"status\": \"CONFIRMED\"}"))
//                 .andExpect(status().isForbidden());
//     }
}
