package com.ordertracker.service;

import com.ordertracker.dto.AuthDTOs;
import com.ordertracker.entity.User;
import com.ordertracker.enums.Role;
import com.ordertracker.exception.DuplicateResourceException;
import com.ordertracker.repository.UserRepository;
import com.ordertracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private AuthDTOs.RegisterRequest registerRequest;
    private AuthDTOs.LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = AuthDTOs.RegisterRequest.builder()
                .fullName("Chaithra Dev")
                .email("chaithra@test.com")
                .password("SecurePass@123")
                .phone("+919876543210")
                .build();

        loginRequest = AuthDTOs.LoginRequest.builder()
                .email("chaithra@test.com")
                .password("SecurePass@123")
                .build();

        savedUser = User.builder()
                .id(1L)
                .fullName("Chaithra Dev")
                .email("chaithra@test.com")
                .password("$2a$12$encodedPassword")
                .phone("+919876543210")
                .role(Role.ROLE_CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("Should register new user and return JWT token")
    void register_WithNewEmail_ShouldSucceed() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked.jwt.token");

        AuthDTOs.AuthResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getEmail()).isEqualTo("chaithra@test.com");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_CUSTOMER);

        verify(passwordEncoder).encode("SecurePass@123");
        verify(userRepository).save(any(User.class));
        verify(jwtService).generateToken(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email already exists")
    void register_WithExistingEmail_ShouldThrowException() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining(registerRequest.getEmail());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void login_WithValidCredentials_ShouldReturnToken() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // Authentication passes
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(savedUser));
        when(jwtService.generateToken(savedUser)).thenReturn("mocked.jwt.token");

        AuthDTOs.AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getEmail()).isEqualTo("chaithra@test.com");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw BadCredentialsException on wrong password")
    void login_WithWrongPassword_ShouldThrowException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(any());
        verifyNoInteractions(jwtService);
    }
}
