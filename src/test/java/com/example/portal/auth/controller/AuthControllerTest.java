package com.example.portal.auth.controller;

import com.example.portal.auth.config.JwtTokenProvider;
import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import com.example.portal.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для AuthController.
 *
 * Тесты покрывают:
 * 1. POST /api/auth/login — вход пользователя
 * 2. GET /api/auth/me — информация о текущем пользователе
 * 3. PUT /api/auth/password — изменение пароля
 * 4. Обработку ошибок (неверный пароль, неаутентифицированный пользователь)
 *
 * Используется чистый Mockito (без Spring Context) для быстроты тестов.
 * SecurityContext устанавливается вручную для имитации аутентификации.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private final UUID testUserId = UUID.randomUUID();
    private final String TEST_USERNAME = "testuser";
    private final String TEST_PASSWORD = "password123";
    private final String TEST_PASSWORD_HASH = "$2a$10$hashedPassword";
    private final String TEST_TOKEN = "jwt.token.here";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        testUser = User.builder()
                .id(testUserId)
                .username(TEST_USERNAME)
                .password(TEST_PASSWORD_HASH)
                .enabled(true)
                .isAdmin(false)
                .createdAt(Instant.now())
                .deletedAt(null)
                .build();
    }

    /**
     * Вспомогательный метод: устанавливает аутентифицированного пользователя в SecurityContext.
     */
    private void setAuthenticatedUser() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                TEST_USERNAME,
                null,
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("POST /api/auth/login — успешный вход")
    void testLogin_Success() {
        // Arrange
        when(userRepository.findByUsernameAndDeletedAtIsNull(TEST_USERNAME)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
        when(tokenProvider.generateToken(TEST_USERNAME, testUserId)).thenReturn(TEST_TOKEN);

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.login(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKeys("token", "username", "isAdmin");
        assertThat(body.get("token")).isEqualTo(TEST_TOKEN);
        assertThat(body.get("username")).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("POST /api/auth/login — неверный пароль")
    void testLogin_WrongPassword() {
        // Arrange
        when(userRepository.findByUsernameAndDeletedAtIsNull(TEST_USERNAME)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword("wrongPassword");

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.login(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("GET /api/auth/me — успешное получение информации о пользователе")
    void testGetCurrentUser_Success() {
        // Arrange
        setAuthenticatedUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull(TEST_USERNAME)).thenReturn(Optional.of(testUser));

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.getCurrentUser();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKeys("username", "isAdmin", "enabled");
        assertThat(body.get("username")).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("GET /api/auth/me — неаутентифицированный пользователь")
    void testGetCurrentUser_Unauthorized() {
        // Arrange
        SecurityContextHolder.clearContext();

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.getCurrentUser();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("PUT /api/auth/password — успешное изменение пароля")
    void testChangePassword_Success() {
        // Arrange
        setAuthenticatedUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull(TEST_USERNAME)).thenReturn(Optional.of(testUser));
        doNothing().when(userService).changePassword(eq(testUserId), anyString(), anyString());

        AuthController.ChangePasswordRequest request = new AuthController.ChangePasswordRequest();
        request.setOldPassword("oldPassword");
        request.setNewPassword("newPassword123");

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.changePassword(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("message");
        verify(userService).changePassword(testUserId, "oldPassword", "newPassword123");
    }

    @Test
    @DisplayName("PUT /api/auth/password — неверный старый пароль")
    void testChangePassword_WrongOldPassword() {
        // Arrange
        setAuthenticatedUser();
        when(userRepository.findByUsernameAndDeletedAtIsNull(TEST_USERNAME)).thenReturn(Optional.of(testUser));
        doThrow(new IllegalArgumentException("Неверный старый пароль"))
                .when(userService).changePassword(eq(testUserId), anyString(), anyString());

        AuthController.ChangePasswordRequest request = new AuthController.ChangePasswordRequest();
        request.setOldPassword("wrongOldPassword");
        request.setNewPassword("newPassword123");

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.changePassword(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("error");
    }

    @Test
    @DisplayName("PUT /api/auth/password — неаутентифицированный пользователь")
    void testChangePassword_Unauthorized() {
        // Arrange
        SecurityContextHolder.clearContext();

        AuthController.ChangePasswordRequest request = new AuthController.ChangePasswordRequest();
        request.setOldPassword("oldPassword");
        request.setNewPassword("newPassword123");

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) authController.changePassword(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
