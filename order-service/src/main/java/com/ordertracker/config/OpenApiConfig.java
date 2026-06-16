package com.ordertracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "BearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce Order Tracking API")
                        .description("""
                                Real-Time E-Commerce Order Tracking System
                                
                                **Features:**
                                - JWT-based stateless authentication
                                - Role-based access control (CUSTOMER / ADMIN)
                                - Real-time order status updates via WebSocket/STOMP
                                - Kafka event streaming between microservices
                                - Redis caching for sub-millisecond reads
                                
                                **Quick Start:**
                                1. Register via `POST /api/v1/auth/register`
                                2. Login via `POST /api/v1/auth/login` to get your JWT token
                                3. Click **Authorize** and enter `Bearer <your_token>`
                                4. Start creating and tracking orders!
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Order Tracker Team")
                                .email("dev@ordertracker.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("API Gateway")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token obtained from /api/v1/auth/login")));
    }
}
