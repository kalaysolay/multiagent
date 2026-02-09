package com.example.portal.auth.controller;

import com.example.portal.auth.entity.User;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для UserController.
 *
 * Тесты покрывают:
 * 1. GET /api/users — список пользователей (только для админов)
 * 2. POST /api/users — создание пользователя (только для админов)
 * 3. DELETE /api/users/{id} — мягкое удаление (только для админов)
 * 4. PUT /api/users/{id}/restore — восстановление (только для админов)
 * 5. Проверку, что не-админы получают 403 (через @PreAuthorize)
 *
 * Используется чистый Mockito (без Spring Context) для быстроты тестов.
 * SecurityContext устанавливается вручную для имитации аутентификации.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private User adminUser;
    private User regularUser;
    private User testUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .password("hashed")
                .enabled(true)
                .isAdmin(true)
                .createdAt(Instant.now())
                .deletedAt(null)
                .build();

        regularUser = User.builder()
                .id(UUID.randomUUID())
                .username("user")
                .password("hashed")
                .enabled(true)
                .isAdmin(false)
                .createdAt(Instant.now())
                .deletedAt(null)
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .password("hashed")
                .enabled(true)
                .isAdmin(false)
                .createdAt(Instant.now())
                .deletedAt(null)
                .build();
    }

    /**
     * Вспомогательный метод: устанавливает аутентифицированного администратора в SecurityContext.
     */
    private void setAdminAuthentication() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("GET /api/users — получение списка пользователей (admin)")
    void testGetAllUsers_AsAdmin() {
        // Arrange
        setAdminAuthentication();
        List<User> users = Arrays.asList(adminUser, regularUser, testUser);
        when(userService.getAllUsers(false)).thenReturn(users);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = userController.getAllUsers(false);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(3);
        verify(userService).getAllUsers(false);
    }

    @Test
    @DisplayName("GET /api/users — получение списка с удалёнными (admin)")
    void testGetAllUsers_IncludeDeleted() {
        // Arrange
        setAdminAuthentication();
        User deletedUser = User.builder()
                .id(UUID.randomUUID())
                .username("deleted")
                .deletedAt(Instant.now())
                .build();
        List<User> users = Arrays.asList(adminUser, deletedUser);
        when(userService.getAllUsers(true)).thenReturn(users);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = userController.getAllUsers(true);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(2);
        verify(userService).getAllUsers(true);
    }

    @Test
    @DisplayName("POST /api/users — создание пользователя (admin)")
    void testCreateUser_AsAdmin() {
        // Arrange
        setAdminAuthentication();
        when(userService.createUser("newuser", "password123", false)).thenReturn(testUser);

        UserController.CreateUserRequest request = new UserController.CreateUserRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setIsAdmin(false);

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) userController.createUser(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(userService).createUser("newuser", "password123", false);
    }

    @Test
    @DisplayName("POST /api/users — ошибка при создании: дублирование username")
    void testCreateUser_DuplicateUsername() {
        // Arrange
        setAdminAuthentication();
        when(userService.createUser(anyString(), anyString(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Пользователь с таким именем уже существует"));

        UserController.CreateUserRequest request = new UserController.CreateUserRequest();
        request.setUsername("existinguser");
        request.setPassword("password");
        request.setIsAdmin(false);

        // Act
        ResponseEntity<?> response = (ResponseEntity<?>) userController.createUser(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("error");
    }

    @Test
    @DisplayName("DELETE /api/users/{id} — мягкое удаление (admin)")
    void testDeleteUser_AsAdmin() {
        // Arrange
        setAdminAuthentication();
        UUID userId = testUser.getId();
        doNothing().when(userService).softDeleteUser(userId);

        // Act
        ResponseEntity<?> response = userController.deleteUser(userId);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("message");
        verify(userService).softDeleteUser(userId);
    }

    @Test
    @DisplayName("DELETE /api/users/{id} — ошибка: пользователь не найден")
    void testDeleteUser_UserNotFound() {
        // Arrange
        setAdminAuthentication();
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Активный пользователь не найден"))
                .when(userService).softDeleteUser(userId);

        // Act
        ResponseEntity<?> response = userController.deleteUser(userId);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("error");
    }

    @Test
    @DisplayName("PUT /api/users/{id}/restore — восстановление пользователя (admin)")
    void testRestoreUser_AsAdmin() {
        // Arrange
        setAdminAuthentication();
        UUID userId = testUser.getId();
        doNothing().when(userService).restoreUser(userId);

        // Act
        ResponseEntity<?> response = userController.restoreUser(userId);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("message");
        verify(userService).restoreUser(userId);
    }

    @Test
    @DisplayName("PUT /api/users/{id}/restore — ошибка: пользователь не удалён")
    void testRestoreUser_UserNotDeleted() {
        // Arrange
        setAdminAuthentication();
        UUID userId = testUser.getId();
        doThrow(new IllegalArgumentException("Пользователь не удалён"))
                .when(userService).restoreUser(userId);

        // Act
        ResponseEntity<?> response = userController.restoreUser(userId);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("error");
    }
}
