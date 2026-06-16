package com.ordertracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordertracker.dto.AuthDTOs;
import com.ordertracker.enums.Role;
import com.ordertracker.exception.DuplicateResourceException;
import com.ordertracker.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private final AuthDTOs.AuthResponse sampleAuthResponse = AuthDTOs.AuthResponse.builder()
            .accessToken("mocked.jwt.token")
            .tokenType("Bearer")
            .userId(1L)
            .email("chaithra@test.com")
            .fullName("Chaithra Dev")
            .role(Role.ROLE_CUSTOMER)
            .build();

    // ─── Register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 201 with token on success")
    void register_WithValidRequest_ShouldReturn201() throws Exception {
        AuthDTOs.RegisterRequest request = AuthDTOs.RegisterRequest.builder()
                .fullName("Chaithra Dev")
                .email("chaithra@test.com")
                .password("SecurePass@123")
                .phone("+919876543210")
                .build();

        when(authService.register(any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.accessToken").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when email is invalid")
    void register_WithInvalidEmail_ShouldReturn400() throws Exception {
        AuthDTOs.RegisterRequest request = AuthDTOs.RegisterRequest.builder()
                .fullName("Chaithra Dev")
                .email("not-an-email")        // ← invalid
                .password("SecurePass@123")
                .phone("+919876543210")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when password too short")
    void register_WithShortPassword_ShouldReturn400() throws Exception {
        AuthDTOs.RegisterRequest request = AuthDTOs.RegisterRequest.builder()
                .fullName("Chaithra Dev")
                .email("chaithra@test.com")
                .password("123")             // ← too short (< 8 chars)
                .phone("+919876543210")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 409 when email already exists")
    void register_WithDuplicateEmail_ShouldReturn409() throws Exception {
        AuthDTOs.RegisterRequest request = AuthDTOs.RegisterRequest.builder()
                .fullName("Chaithra Dev")
                .email("chaithra@test.com")
                .password("SecurePass@123")
                .phone("+919876543210")
                .build();

        when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/login - Should return 200 with token on success")
    void login_WithValidCredentials_ShouldReturn200() throws Exception {
        AuthDTOs.LoginRequest request = AuthDTOs.LoginRequest.builder()
                .email("chaithra@test.com")
                .password("SecurePass@123")
                .build();

        when(authService.login(any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should return 401 on bad credentials")
    void login_WithWrongPassword_ShouldReturn401() throws Exception {
        AuthDTOs.LoginRequest request = AuthDTOs.LoginRequest.builder()
                .email("chaithra@test.com")
                .password("WrongPassword")
                .build();

        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should return 400 when email is missing")
    void login_WithMissingEmail_ShouldReturn400() throws Exception {
        String requestBody = "{\"password\": \"SecurePass@123\"}"; // no email field

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
