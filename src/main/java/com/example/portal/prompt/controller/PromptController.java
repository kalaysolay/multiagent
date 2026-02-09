package com.example.portal.prompt.controller;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import com.example.portal.prompt.entity.Prompt;
import com.example.portal.prompt.entity.PromptHistory;
import com.example.portal.prompt.service.PromptService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST-контроллер для управления справочником промптов.
 * <p>
 * Эндпоинты:
 * - GET  /api/prompts          — список всех промптов (только для админов)
 * - GET  /api/prompts/{code}   — получить один промпт по коду
 * - PUT  /api/prompts/{code}   — обновить текст промпта (только для админов)
 * - GET  /api/prompts/{code}/history — получить историю изменений
 * <p>
 * Защита: PUT-операции доступны только пользователям с is_admin = true.
 */
@Slf4j
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;
    private final UserRepository userRepository;

    /**
     * Получить список всех промптов.
     * Возвращает краткую информацию без полного текста (для экономии трафика).
     * Доступно только администраторам.
     */
    @GetMapping
    public ResponseEntity<?> getAllPrompts() {
        // Проверяем, что текущий пользователь — администратор
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }

        List<Prompt> prompts = promptService.getAll();

        // Возвращаем список с краткой информацией
        var result = prompts.stream().map(p -> Map.of(
                "code", p.getCode(),
                "name", p.getName(),
                "description", p.getDescription() != null ? p.getDescription() : "",
                "updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : "",
                "contentLength", p.getContent().length()
        )).toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Получить один промпт по коду (полный текст).
     * Доступно только администраторам.
     */
    @GetMapping("/{code}")
    public ResponseEntity<?> getPromptByCode(@PathVariable String code) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }

        return promptService.findByCode(code)
                .map(p -> ResponseEntity.ok(Map.of(
                        "code", p.getCode(),
                        "name", p.getName(),
                        "content", p.getContent(),
                        "description", p.getDescription() != null ? p.getDescription() : "",
                        "updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Обновить текст промпта.
     * Старая версия автоматически сохраняется в историю.
     * Доступно только администраторам.
     */
    @PutMapping("/{code}")
    public ResponseEntity<?> updatePrompt(@PathVariable String code,
                                          @RequestBody UpdatePromptRequest request) {
        // Проверяем права администратора
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }

        try {
            Prompt updated = promptService.updatePrompt(
                    code,
                    request.getContent(),
                    currentUser.getId(),
                    request.getReason()
            );

            log.info("Промпт '{}' обновлён пользователем '{}'", code, currentUser.getUsername());

            return ResponseEntity.ok(Map.of(
                    "code", updated.getCode(),
                    "name", updated.getName(),
                    "content", updated.getContent(),
                    "updatedAt", updated.getUpdatedAt().toString(),
                    "message", "Промпт успешно обновлён"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Получить историю изменений промпта.
     * Доступно только администраторам.
     */
    @GetMapping("/{code}/history")
    public ResponseEntity<?> getPromptHistory(@PathVariable String code) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }

        try {
            List<PromptHistory> history = promptService.getHistory(code);

            var result = history.stream().map(h -> Map.of(
                    "id", h.getId().toString(),
                    "content", h.getContent(),
                    "changedAt", h.getChangedAt().toString(),
                    "changeReason", h.getChangeReason() != null ? h.getChangeReason() : ""
            )).toList();

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Получить текущего аутентифицированного пользователя из SecurityContext.
     * Возвращает null, если пользователь не аутентифицирован.
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        String username = auth.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * DTO для запроса обновления промпта.
     */
    @Data
    static class UpdatePromptRequest {
        /** Новый текст промпта */
        private String content;
        /** Причина изменения (опционально) */
        private String reason;
    }
}
