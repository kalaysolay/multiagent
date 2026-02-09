package com.example.portal.prompt.service;

import com.example.portal.prompt.entity.Prompt;
import com.example.portal.prompt.entity.PromptHistory;
import com.example.portal.prompt.repository.PromptHistoryRepository;
import com.example.portal.prompt.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для PromptService.
 *
 * Тесты покрывают:
 * 1. Получение промпта по коду (с кэшированием)
 * 2. Получение списка всех промптов
 * 3. Обновление промпта с сохранением истории
 * 4. Инвалидацию кэша при обновлении
 * 5. Обработку ошибок (промпт не найден)
 *
 * Используется Mockito для мокирования репозиториев,
 * чтобы тесты не зависели от базы данных.
 */
@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    // Мокируем репозитории — в тестах не нужна реальная БД
    @Mock
    private PromptRepository promptRepository;

    @Mock
    private PromptHistoryRepository promptHistoryRepository;

    // Тестируемый сервис — Mockito автоматически подставит моки
    @InjectMocks
    private PromptService promptService;

    // Тестовые данные, которые используются в нескольких тестах
    private Prompt testPrompt;
    private final UUID testUserId = UUID.randomUUID();
    private final String TEST_CODE = "domain_modeller_system";
    private final String TEST_CONTENT = "Ты мастер по описанию требований к ПО...";

    /**
     * Подготовка тестовых данных перед каждым тестом.
     * @BeforeEach означает, что этот метод вызывается перед КАЖДЫМ @Test-методом.
     */
    @BeforeEach
    void setUp() {
        // Создаём тестовый промпт
        testPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .code(TEST_CODE)
                .name("Domain Modeller — системный промпт")
                .content(TEST_CONTENT)
                .description("Тестовый промпт")
                .updatedAt(Instant.now())
                .build();

        // Очищаем кэш сервиса перед каждым тестом,
        // чтобы тесты не влияли друг на друга
        promptService.clearCache();
    }

    // ====================================================================
    // Тесты для метода getByCode
    // ====================================================================

    @Test
    @DisplayName("getByCode — загружает промпт из БД при первом обращении")
    void getByCode_loadsFromDatabaseOnFirstCall() {
        // GIVEN (Дано): в БД есть промпт с кодом "domain_modeller_system"
        when(promptRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(testPrompt));

        // WHEN (Когда): запрашиваем промпт по коду
        String result = promptService.getByCode(TEST_CODE);

        // THEN (Тогда): получаем текст промпта и обращение к БД было ровно 1 раз
        assertThat(result).isEqualTo(TEST_CONTENT);
        verify(promptRepository, times(1)).findByCode(TEST_CODE);
    }

    @Test
    @DisplayName("getByCode — при повторном обращении возвращает из кэша (без БД)")
    void getByCode_returnsCachedValueOnSecondCall() {
        // GIVEN: в БД есть промпт
        when(promptRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(testPrompt));

        // WHEN: запрашиваем промпт дважды
        promptService.getByCode(TEST_CODE);    // первый вызов — идёт в БД
        String result = promptService.getByCode(TEST_CODE);  // второй вызов — из кэша

        // THEN: к БД обратились только 1 раз (второй вызов из кэша)
        assertThat(result).isEqualTo(TEST_CONTENT);
        verify(promptRepository, times(1)).findByCode(TEST_CODE);
    }

    @Test
    @DisplayName("getByCode — бросает исключение, если промпт не найден")
    void getByCode_throwsIfNotFound() {
        // GIVEN: в БД нет промпта с таким кодом
        when(promptRepository.findByCode("non_existent")).thenReturn(Optional.empty());

        // WHEN + THEN: ожидаем IllegalArgumentException
        assertThatThrownBy(() -> promptService.getByCode("non_existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non_existent");
    }

    // ====================================================================
    // Тесты для метода getAll
    // ====================================================================

    @Test
    @DisplayName("getAll — возвращает список всех промптов из БД")
    void getAll_returnsAllPrompts() {
        // GIVEN: в БД два промпта
        Prompt prompt2 = Prompt.builder()
                .id(UUID.randomUUID())
                .code("evaluator_plantuml")
                .name("Evaluator")
                .content("Content 2")
                .updatedAt(Instant.now())
                .build();
        when(promptRepository.findAll()).thenReturn(List.of(testPrompt, prompt2));

        // WHEN
        List<Prompt> result = promptService.getAll();

        // THEN
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo(TEST_CODE);
        assertThat(result.get(1).getCode()).isEqualTo("evaluator_plantuml");
    }

    // ====================================================================
    // Тесты для метода updatePrompt
    // ====================================================================

    @Test
    @DisplayName("updatePrompt — сохраняет старую версию в историю и обновляет промпт")
    void updatePrompt_savesHistoryAndUpdatesPrompt() {
        // GIVEN: в БД есть промпт
        when(promptRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(testPrompt));
        when(promptRepository.save(any(Prompt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(promptHistoryRepository.save(any(PromptHistory.class))).thenAnswer(inv -> {
            PromptHistory h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        String newContent = "Обновлённый текст промпта";

        // WHEN: обновляем промпт
        Prompt updated = promptService.updatePrompt(TEST_CODE, newContent, testUserId, "Улучшение формулировки");

        // THEN: промпт обновлён
        assertThat(updated.getContent()).isEqualTo(newContent);
        assertThat(updated.getUpdatedBy()).isEqualTo(testUserId);

        // Проверяем, что старая версия сохранена в историю
        ArgumentCaptor<PromptHistory> historyCaptor = ArgumentCaptor.forClass(PromptHistory.class);
        verify(promptHistoryRepository).save(historyCaptor.capture());

        PromptHistory savedHistory = historyCaptor.getValue();
        // В историю должен попасть СТАРЫЙ текст (до обновления)
        assertThat(savedHistory.getContent()).isEqualTo(TEST_CONTENT);
        assertThat(savedHistory.getChangedBy()).isEqualTo(testUserId);
        assertThat(savedHistory.getChangeReason()).isEqualTo("Улучшение формулировки");
    }

    @Test
    @DisplayName("updatePrompt — инвалидирует кэш после обновления")
    void updatePrompt_invalidatesCache() {
        // GIVEN: промпт закэширован
        when(promptRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(testPrompt));
        when(promptRepository.save(any(Prompt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(promptHistoryRepository.save(any(PromptHistory.class))).thenAnswer(inv -> {
            PromptHistory h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        // Прогреваем кэш
        promptService.getByCode(TEST_CODE);

        // WHEN: обновляем промпт с новым текстом
        String newContent = "Совершенно новый текст";
        testPrompt.setContent(newContent); // Имитируем обновление
        promptService.updatePrompt(TEST_CODE, newContent, testUserId, null);

        // Готовим мок для следующего запроса — теперь вернётся обновлённый промпт
        Prompt updatedPrompt = Prompt.builder()
                .id(testPrompt.getId())
                .code(TEST_CODE)
                .name(testPrompt.getName())
                .content(newContent)
                .updatedAt(Instant.now())
                .build();
        when(promptRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(updatedPrompt));

        // THEN: следующий getByCode должен снова обратиться к БД (кэш инвалидирован)
        String result = promptService.getByCode(TEST_CODE);
        assertThat(result).isEqualTo(newContent);

        // findByCode вызывался: 1 раз при первом getByCode, 1 раз при updatePrompt, 1 раз при втором getByCode
        verify(promptRepository, times(3)).findByCode(TEST_CODE);
    }

    @Test
    @DisplayName("updatePrompt — бросает исключение, если промпт не найден")
    void updatePrompt_throwsIfNotFound() {
        // GIVEN: промпта нет в БД
        when(promptRepository.findByCode("non_existent")).thenReturn(Optional.empty());

        // WHEN + THEN
        assertThatThrownBy(() ->
                promptService.updatePrompt("non_existent", "new content", testUserId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non_existent");
    }

    // ====================================================================
    // Тест для clearCache
    // ====================================================================

    @Test
    @DisplayName("clearCache — после очистки кэша getByCode снова обращается к БД")
    void clearCache_forcesReloadFromDatabase() {
        // GIVEN: промпт в кэше
        when(promptRepository.findByCode(TEST_CODE)).thenReturn(Optional.of(testPrompt));
        promptService.getByCode(TEST_CODE); // Прогреваем кэш

        // WHEN: очищаем кэш
        promptService.clearCache();
        promptService.getByCode(TEST_CODE); // Должен снова пойти в БД

        // THEN: к БД обратились 2 раза (до очистки и после)
        verify(promptRepository, times(2)).findByCode(TEST_CODE);
    }
}
