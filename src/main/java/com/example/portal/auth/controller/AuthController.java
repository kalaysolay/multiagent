package com.example.portal.auth.controller;

import com.example.portal.auth.config.JwtTokenProvider;
import com.example.portal.auth.repository.UserRepository;
import com.example.portal.auth.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Контроллер аутентификации.
 * Эндпоинты:
 * - POST /api/auth/login — вход по логину/паролю, возвращает JWT + isAdmin
 * - GET  /api/auth/me    — информация о текущем пользователе (нужна фронтенду для проверки роли)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    
    /**
     * Аутентификация пользователя по логину и паролю.
     * При успехе возвращает JWT-токен, имя пользователя и флаг isAdmin.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return userRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .filter(user -> user.getEnabled())
                .filter(user -> !user.isDeleted()) // Дополнительная проверка
                .map(user -> {
                    String token = tokenProvider.generateToken(user.getUsername(), user.getId());
                    // Возвращаем токен, имя пользователя и флаг администратора
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "username", user.getUsername(),
                            "isAdmin", Boolean.TRUE.equals(user.getIsAdmin())
                    ));
                })
                .orElse(ResponseEntity.status(401)
                        .body(Map.of("error", "Неверный логин или пароль")));
    }
    
    /**
     * Получить информацию о текущем аутентифицированном пользователе.
     * Фронтенд использует этот эндпоинт для проверки валидности токена
     * и получения роли пользователя (isAdmin).
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "Не аутентифицирован"));
        }

        String username = auth.getName();
        return userRepository.findByUsernameAndDeletedAtIsNull(username)
                .map(user -> ResponseEntity.ok(Map.of(
                        "username", user.getUsername(),
                        "isAdmin", Boolean.TRUE.equals(user.getIsAdmin()),
                        "enabled", user.getEnabled()
                )))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Пользователь не найден")));
    }
    
    /**
     * Изменяет пароль текущего аутентифицированного пользователя.
     * Требует указания старого пароля для подтверждения.
     * 
     * @param request запрос с oldPassword и newPassword
     * @return успешное сообщение или ошибка
     */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "Не аутентифицирован"));
        }

        // Получаем ID пользователя из JWT токена (сохранён в JwtTokenProvider)
        String username = auth.getName();
        UUID userId = userRepository.findByUsernameAndDeletedAtIsNull(username)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        try {
            userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Пароль успешно изменён"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * DTO для запроса входа.
     */
    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }
    
    /**
     * DTO для запроса изменения пароля.
     */
    @Data
    static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
