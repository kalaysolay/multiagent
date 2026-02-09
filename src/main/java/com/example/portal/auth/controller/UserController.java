package com.example.portal.auth.controller;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Контроллер для управления пользователями (только для администраторов).
 * 
 * Эндпоинты:
 * - GET    /api/users              — список всех пользователей
 * - POST   /api/users               — создание нового пользователя
 * - DELETE /api/users/{id}         — мягкое удаление пользователя
 * - PUT    /api/users/{id}/restore  — восстановление удалённого пользователя
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /**
     * Получает список всех пользователей.
     * 
     * @param includeDeleted если true, включает удалённых пользователей
     * @return список пользователей (без паролей)
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        List<User> users = userService.getAllUsers(includeDeleted);
        
        List<Map<String, Object>> result = users.stream()
                .map(this::userToMap)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Создаёт нового пользователя.
     * 
     * @param request данные пользователя (username, password, isAdmin)
     * @return созданный пользователь (без пароля)
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            User user = userService.createUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getIsAdmin()
            );
            
            return ResponseEntity.ok(userToMap(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Выполняет мягкое удаление пользователя (устанавливает deleted_at).
     * 
     * @param id ID пользователя
     * @return сообщение об успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        try {
            userService.softDeleteUser(id);
            return ResponseEntity.ok(Map.of("message", "Пользователь удалён"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Восстанавливает удалённого пользователя (очищает deleted_at).
     * 
     * @param id ID пользователя
     * @return сообщение об успехе
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<?> restoreUser(@PathVariable UUID id) {
        try {
            userService.restoreUser(id);
            return ResponseEntity.ok(Map.of("message", "Пользователь восстановлен"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Преобразует User в Map для JSON ответа (без пароля).
     */
    private Map<String, Object> userToMap(User user) {
        return Map.of(
                "id", user.getId().toString(),
                "username", user.getUsername(),
                "isAdmin", Boolean.TRUE.equals(user.getIsAdmin()),
                "enabled", user.getEnabled(),
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "",
                "deletedAt", user.getDeletedAt() != null ? user.getDeletedAt().toString() : ""
        );
    }

    /**
     * DTO для запроса создания пользователя.
     */
    @Data
    static class CreateUserRequest {
        private String username;
        private String password;
        private Boolean isAdmin;
    }
}
