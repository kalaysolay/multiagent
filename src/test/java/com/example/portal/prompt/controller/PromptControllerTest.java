package com.example.portal.prompt.controller;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import com.example.portal.prompt.entity.Prompt;
import com.example.portal.prompt.service.PromptService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для PromptController.
 *
 * Тесты покрывают:
 * 1. GET /api/prompts — список промптов (только для админов)
 * 2. GET /api/prompts/{code} — получение одного промпта
 * 3. PUT /api/prompts/{code} — обновление промпта (только для админов)
 * 4. Проверку, что не-админы получают 403
 *
 * Используется чистый Mockito (без Spring Context) для быстроты тестов.
 * SecurityContext устанавливается вручную для имитации аутентификации.
 */
@ExtendWith(MockitoExtension.class)
class PromptControllerTest {

    // Мокируем зависимости контроллера
    @Mock
    private PromptService promptService;

    @Mock
    private UserRepository userRepository;

    // Тестируемый контроллер
    @InjectMocks
    private PromptController promptController;

    // Тестовые данные
    private User adminUser;
    private User regularUser;
    private Prompt testPrompt;

    /**
     * Подготовка тестовых данных перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        // Очищаем SecurityContext перед каждым тестом
        SecurityContextHolder.clearContext();

        // Пользователь-администратор
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .password("hashed")
                .enabled(true)
                .isAdmin(true)
                .createdAt(Instant.now())
                .build();

        // Обычный пользователь (не администратор)
        regularUser = User.builder()
                .id(UUID.randomUUID())
                .username("user")
                .password("hashed")
                .enabled(true)
                .isAdmin(false)
                .createdAt(Instant.now())
                .build();

        // Тестовый промпт
        testPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .code("domain_modeller_system")
                .name("Domain Modeller — системный промпт")
                .content("Ты мастер по описанию требований к ПО...")
                .description("Системный промпт для DomainModellerService")
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Вспомогательный метод: устанавливает аутентифицированного пользователя в SecurityContext.
     * Имитирует поведение Spring Security после успешной JWT-аутентификации.
     */
    private void setAuthenticatedUser(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ====================================================================
    // Тесты GET /api/prompts
    // ====================================================================

    @Test
    @DisplayName("GET /api/prompts — админ получает список промптов (200)")
    void getAllPrompts_admin_returnsOk() {
        // GIVEN: пользователь — администратор
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(promptService.getAll()).thenReturn(List.of(testPrompt));

        // WHEN: вызываем метод контроллера
        ResponseEntity<?> response = promptController.getAllPrompts();

        // THEN: статус 200, в теле — массив с промптом
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        var body = (List<Map<String, Object>>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("code")).isEqualTo("domain_modeller_system");
    }

    @Test
    @DisplayName("GET /api/prompts — обычный пользователь получает 403")
    void getAllPrompts_regularUser_returnsForbidden() {
        // GIVEN: пользователь — не администратор
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        // WHEN
        ResponseEntity<?> response = promptController.getAllPrompts();

        // THEN: статус 403
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /api/prompts — неаутентифицированный пользователь получает 403")
    void getAllPrompts_unauthenticated_returnsForbidden() {
        // GIVEN: SecurityContext пуст (нет аутентификации)
        // WHEN
        ResponseEntity<?> response = promptController.getAllPrompts();

        // THEN: статус 403 (getCurrentUser() вернёт null)
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    // ====================================================================
    // Тесты GET /api/prompts/{code}
    // ====================================================================

    @Test
    @DisplayName("GET /api/prompts/{code} — админ получает промпт с полным текстом")
    void getPromptByCode_admin_returnsPrompt() {
        // GIVEN
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(promptService.findByCode("domain_modeller_system")).thenReturn(Optional.of(testPrompt));

        // WHEN
        ResponseEntity<?> response = promptController.getPromptByCode("domain_modeller_system");

        // THEN
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("domain_modeller_system");
        assertThat(body.get("content")).isEqualTo("Ты мастер по описанию требований к ПО...");
    }

    @Test
    @DisplayName("GET /api/prompts/{code} — несуществующий код возвращает 404")
    void getPromptByCode_notFound_returns404() {
        // GIVEN
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(promptService.findByCode("non_existent")).thenReturn(Optional.empty());

        // WHEN
        ResponseEntity<?> response = promptController.getPromptByCode("non_existent");

        // THEN
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /api/prompts/{code} — обычный пользователь получает 403")
    void getPromptByCode_regularUser_returnsForbidden() {
        // GIVEN
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        // WHEN
        ResponseEntity<?> response = promptController.getPromptByCode("domain_modeller_system");

        // THEN
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        // findByCode не должен вызываться для не-админа
        verify(promptService, never()).findByCode(any());
    }

    // ====================================================================
    // Тесты PUT /api/prompts/{code}
    // ====================================================================

    @Test
    @DisplayName("PUT /api/prompts/{code} — админ успешно обновляет промпт")
    void updatePrompt_admin_returnsUpdated() {
        // GIVEN: обновлённый промпт
        Prompt updatedPrompt = Prompt.builder()
                .id(testPrompt.getId())
                .code("domain_modeller_system")
                .name("Domain Modeller — системный промпт")
                .content("Новый текст промпта")
                .updatedAt(Instant.now())
                .build();

        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(promptService.updatePrompt(eq("domain_modeller_system"), eq("Новый текст промпта"),
                eq(adminUser.getId()), eq("Тестовое изменение")))
                .thenReturn(updatedPrompt);

        // Создаём DTO запроса через рефлексию (класс package-private)
        // Используем метод контроллера напрямую
        var request = new PromptController.UpdatePromptRequest();
        request.setContent("Новый текст промпта");
        request.setReason("Тестовое изменение");

        // WHEN
        ResponseEntity<?> response = promptController.updatePrompt("domain_modeller_system", request);

        // THEN: статус 200, промпт обновлён
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("domain_modeller_system");
        assertThat(body.get("content")).isEqualTo("Новый текст промпта");
        assertThat(body.get("message")).isEqualTo("Промпт успешно обновлён");
    }

    @Test
    @DisplayName("PUT /api/prompts/{code} — обычный пользователь получает 403")
    void updatePrompt_regularUser_returnsForbidden() {
        // GIVEN: пользователь — не администратор
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        var request = new PromptController.UpdatePromptRequest();
        request.setContent("Попытка изменения");

        // WHEN
        ResponseEntity<?> response = promptController.updatePrompt("domain_modeller_system", request);

        // THEN: статус 403
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        // Проверяем, что updatePrompt НЕ был вызван
        verify(promptService, never()).updatePrompt(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PUT /api/prompts/{code} — несуществующий код возвращает 404")
    void updatePrompt_notFound_returns404() {
        // GIVEN
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(promptService.updatePrompt(eq("non_existent"), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Промпт с кодом 'non_existent' не найден"));

        var request = new PromptController.UpdatePromptRequest();
        request.setContent("Новый текст");

        // WHEN
        ResponseEntity<?> response = promptController.updatePrompt("non_existent", request);

        // THEN
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
