package com.example.portal.auth.service;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для управления пользователями.
 * 
 * Предоставляет методы для:
 * - Изменения пароля пользователя
 * - Получения списка пользователей
 * - Создания новых пользователей
 * - Мягкого удаления и восстановления пользователей
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Изменяет пароль пользователя.
     * 
     * @param userId ID пользователя
     * @param oldPassword старый пароль (для проверки)
     * @param newPassword новый пароль
     * @throws IllegalArgumentException если старый пароль неверен или пользователь не найден
     */
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Проверяем старый пароль
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Неверный старый пароль");
        }

        // Валидация нового пароля
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Новый пароль не может быть пустым");
        }
        if (newPassword.length() < 3) {
            throw new IllegalArgumentException("Новый пароль должен содержать минимум 3 символа");
        }

        // Хешируем и сохраняем новый пароль
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Пароль изменён для пользователя: {}", user.getUsername());
    }

    /**
     * Получает список всех пользователей.
     * 
     * @param includeDeleted если true, включает удалённых пользователей
     * @return список пользователей
     */
    public List<User> getAllUsers(boolean includeDeleted) {
        if (includeDeleted) {
            return userRepository.findAll();
        } else {
            return userRepository.findAllByDeletedAtIsNull();
        }
    }

    /**
     * Создаёт нового пользователя.
     * 
     * @param username имя пользователя
     * @param password пароль (будет захеширован)
     * @param isAdmin флаг администратора
     * @return созданный пользователь
     * @throws IllegalArgumentException если username уже существует или невалидные данные
     */
    @Transactional
    public User createUser(String username, String password, Boolean isAdmin) {
        // Валидация username
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("Имя пользователя должно содержать от 3 до 50 символов");
        }

        // Проверка уникальности среди активных пользователей
        if (userRepository.existsByUsernameAndDeletedAtIsNull(username)) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }

        // Валидация пароля
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        if (password.length() < 3) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 3 символа");
        }

        // Создаём пользователя
        User user = User.builder()
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .isAdmin(isAdmin != null && isAdmin)
                .enabled(true)
                .deletedAt(null)
                .build();

        User saved = userRepository.save(user);
        log.info("Создан пользователь: {} (admin: {})", saved.getUsername(), saved.getIsAdmin());
        
        return saved;
    }

    /**
     * Выполняет мягкое удаление пользователя (устанавливает deleted_at).
     * 
     * @param userId ID пользователя
     * @throws IllegalArgumentException если пользователь не найден
     */
    @Transactional
    public void softDeleteUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("Активный пользователь не найден"));

        if (user.isDeleted()) {
            throw new IllegalArgumentException("Пользователь уже удалён");
        }

        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        
        log.info("Пользователь мягко удалён: {} (id: {})", user.getUsername(), userId);
    }

    /**
     * Восстанавливает удалённого пользователя (очищает deleted_at).
     * 
     * @param userId ID пользователя
     * @throws IllegalArgumentException если пользователь не найден или не удалён
     */
    @Transactional
    public void restoreUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (!user.isDeleted()) {
            throw new IllegalArgumentException("Пользователь не удалён");
        }

        user.setDeletedAt(null);
        userRepository.save(user);
        
        log.info("Пользователь восстановлен: {} (id: {})", user.getUsername(), userId);
    }
}
