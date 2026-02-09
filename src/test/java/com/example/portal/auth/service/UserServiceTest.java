package com.example.portal.auth.service;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для UserService.
 *
 * Тесты покрывают:
 * 1. Изменение пароля пользователя (успех и ошибки)
 * 2. Получение списка пользователей (с фильтрацией удалённых)
 * 3. Создание пользователя (успех и валидация)
 * 4. Мягкое удаление пользователя
 * 5. Восстановление удалённого пользователя
 * 6. Обработку ошибок (пользователь не найден, дублирование username)
 *
 * Используется Mockito для мокирования репозиториев и PasswordEncoder,
 * чтобы тесты не зависели от базы данных.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private final UUID testUserId = UUID.randomUUID();
    private final String TEST_USERNAME = "testuser";
    private final String TEST_PASSWORD = "oldPassword123";
    private final String TEST_PASSWORD_HASH = "$2a$10$hashedPassword";

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("Успешное изменение пароля")
    void testChangePassword_Success() {
        // Arrange
        String newPassword = "newPassword123";
        String newPasswordHash = "$2a$10$newHashedPassword";

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(newPasswordHash);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.changePassword(testUserId, TEST_PASSWORD, newPassword);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo(newPasswordHash);
    }

    @Test
    @DisplayName("Ошибка при изменении пароля: неверный старый пароль")
    void testChangePassword_WrongOldPassword() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(testUserId, TEST_PASSWORD, "newPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неверный старый пароль");
    }

    @Test
    @DisplayName("Ошибка при изменении пароля: пользователь не найден")
    void testChangePassword_UserNotFound() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(testUserId, TEST_PASSWORD, "newPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пользователь не найден");
    }

    @Test
    @DisplayName("Ошибка при изменении пароля: новый пароль слишком короткий")
    void testChangePassword_NewPasswordTooShort() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(testUserId, TEST_PASSWORD, "ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("минимум 3 символа");
    }

    @Test
    @DisplayName("Получение всех пользователей (включая удалённых)")
    void testGetAllUsers_IncludeDeleted() {
        // Arrange
        User deletedUser = User.builder()
                .id(UUID.randomUUID())
                .username("deleted")
                .deletedAt(Instant.now())
                .build();

        List<User> allUsers = Arrays.asList(testUser, deletedUser);
        when(userRepository.findAll()).thenReturn(allUsers);

        // Act
        List<User> result = userService.getAllUsers(true);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).contains(testUser, deletedUser);
    }

    @Test
    @DisplayName("Получение только активных пользователей")
    void testGetAllUsers_ExcludeDeleted() {
        // Arrange
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(Arrays.asList(testUser));

        // Act
        List<User> result = userService.getAllUsers(false);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result).contains(testUser);
    }

    @Test
    @DisplayName("Успешное создание пользователя")
    void testCreateUser_Success() {
        // Arrange
        String username = "newuser";
        String password = "password123";
        String passwordHash = "$2a$10$hashed";

        when(userRepository.existsByUsernameAndDeletedAtIsNull(username)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(passwordHash);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        // Act
        User result = userService.createUser(username, password, false);

        // Assert
        assertThat(result.getUsername()).isEqualTo(username);
        assertThat(result.getPassword()).isEqualTo(passwordHash);
        assertThat(result.getIsAdmin()).isFalse();
        assertThat(result.getDeletedAt()).isNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Ошибка при создании пользователя: дублирование username")
    void testCreateUser_DuplicateUsername() {
        // Arrange
        String username = "existinguser";
        when(userRepository.existsByUsernameAndDeletedAtIsNull(username)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(username, "password", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("уже существует");
    }

    @Test
    @DisplayName("Ошибка при создании пользователя: username слишком короткий")
    void testCreateUser_UsernameTooShort() {
        // Arrange
        when(userRepository.existsByUsernameAndDeletedAtIsNull("ab")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser("ab", "password", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("от 3 до 50 символов");
    }

    @Test
    @DisplayName("Успешное мягкое удаление пользователя")
    void testSoftDeleteUser_Success() {
        // Arrange
        when(userRepository.findByIdAndDeletedAtIsNull(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.softDeleteUser(testUserId);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ошибка при мягком удалении: пользователь не найден")
    void testSoftDeleteUser_UserNotFound() {
        // Arrange
        when(userRepository.findByIdAndDeletedAtIsNull(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.softDeleteUser(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Успешное восстановление пользователя")
    void testRestoreUser_Success() {
        // Arrange
        testUser.setDeletedAt(Instant.now());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.restoreUser(testUserId);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Ошибка при восстановлении: пользователь не удалён")
    void testRestoreUser_UserNotDeleted() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.restoreUser(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не удалён");
    }
}
