package com.example.portal.auth.controller;

import com.example.portal.auth.config.JwtTokenProvider;
import com.example.portal.auth.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    
    /**
     * Аутентификация пользователя по логину и паролю.
     * При успехе возвращает JWT-токен, имя пользователя и флаг isAdmin.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .filter(user -> user.getEnabled())
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
        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(Map.of(
                        "username", user.getUsername(),
                        "isAdmin", Boolean.TRUE.equals(user.getIsAdmin()),
                        "enabled", user.getEnabled()
                )))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Пользователь не найден")));
    }
    
    /**
     * DTO для запроса входа.
     */
    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }
}
