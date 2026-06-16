package com.ordertracker.service;

import com.ordertracker.entity.User;
import com.ordertracker.enums.Role;
import com.ordertracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inject private fields via ReflectionTestUtils (avoids needing Spring context)
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                "test-secret-key-that-is-long-enough-for-hmac-sha256-signing-algorithm");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L);

        testUser = User.builder()
                .id(1L)
                .email("chaithra@test.com")
                .password("encodedPassword")
                .role(Role.ROLE_CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("Should generate a valid JWT token")
    void generateToken_ShouldReturnNonEmptyToken() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotNull().isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // Header.Payload.Signature
    }

    @Test
    @DisplayName("Should extract correct username from token")
    void extractUsername_ShouldReturnCorrectEmail() {
        String token = jwtService.generateToken(testUser);

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("chaithra@test.com");
    }

    @Test
    @DisplayName("Should validate token against correct user")
    void isTokenValid_WithCorrectUser_ShouldReturnTrue() {
        String token = jwtService.generateToken(testUser);

        boolean valid = jwtService.isTokenValid(token, testUser);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should reject token for wrong user")
    void isTokenValid_WithWrongUser_ShouldReturnFalse() {
        String token = jwtService.generateToken(testUser);

        User wrongUser = User.builder()
                .id(2L)
                .email("other@test.com")
                .password("pass")
                .role(Role.ROLE_CUSTOMER)
                .build();

        boolean valid = jwtService.isTokenValid(token, wrongUser);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should reject an expired token")
    void isTokenValid_WithExpiredToken_ShouldReturnFalse() {
        // Set expiry to 1ms so token expires immediately
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 1L);

        String token = jwtService.generateToken(testUser);

        // Token generated with 1ms expiry — will be expired by now
        boolean valid = jwtService.isTokenValid(token, testUser);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should reject a tampered token")
    void isTokenValid_WithTamperedToken_ShouldReturnFalse() {
        String token = jwtService.generateToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        boolean valid = jwtService.isTokenValid(tampered, testUser);

        assertThat(valid).isFalse();
    }
}
