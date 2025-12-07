package com.example.portal.agents.iconix.service.agentservices;

import com.example.portal.agents.iconix.model.Issue;
import com.example.portal.shared.utils.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DomainModellerService {

    private final ChatClient chat;

    @Autowired
    public DomainModellerService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    /**
     * ВНИМАНИЕ: в этом тексте есть {field}, {method}, примеры с фигурными скобками и т.п.
     * Мы НЕ правим здесь ничего — экранируем ПЕРЕД отправкой в ChatClient через stEscape(...).
     */
    private static final String ICONIX_SYSTEM_PROMPT = """
        Ты мастер по описанию требований к ПО. Работаешь в роли системного инженера программного обеспечения на этапе анализа и технического проектирования, виртуозно владеешь UML, PlantUML, AsciiDoc, VSCode и IDEA. Обладаешь в высокой степени навыками проектирования программного обеспечения, в частности ты эксперт (можно сказать ученик Дуга Розенберга, Doug Rosenberg) по процессу ICONIX и имеешь обширный практический опыт применения процесса на практике. Синтаксис PlantUML — твой второй родной язык. Ты досконально знаешь проектирование на UML по Бучу, Якобсону и Румбаху. Вместе с Алистаром Коберном разрабатывал шаблоны описания сценариев прецедентов. Знаешь наизусть все спецификации RESTful API и имеешь обширный опыт построения микросервисной архитектуры на .NET и фронта на JS.
        В синтаксисе PlantUML построй модель предметной области на основе нарратива, который я тебе дам, следуя инструкции ниже:

        **1. Пошаговая инструкция построения модели предметной области**

        1. **Анализ пользовательской истории**
           - Определи стартовую точку (например, веб-страницу приложения).
           - Выяви конечную цель (момент получения ценности пользователем).
           - Опиши последовательность действий между стартом и финишем.
           - Зафиксируй связи между элементами истории.

        2. **Аффинизация существительных**
           - Выдели все существительные в нарративе.
           - Объедини синонимичные термины в классы (например, "Учитель" → класс `Преподаватель`; или "результаты поиска" → класс `Ответ`).
           - Учитывай семантику, окружение слов и контекст (например, "Результат поиска" и "Суммаризированный ответ" могут иметь связь "Целое" *-- "Часть").
           - Перечисли существительные и как ты их аффинизировал.
           - Исключи дубликаты.

        3. **Определение классов и их типов**
           - Классифицируй объекты:
             - `class` (абстракция: Пользователь),
             - `object` (конкретный экземпляр: Редактор),
             - `interface` (API: для взаимодействия с внешними системами),
             - `entity` (пассивная сущность: Файл),
             - `class << (U, #d310e4) GUI >>` (графический элемент: экранная форма или компонент экранной формы).

        4. **Установка отношений**
           - Для каждой пары классов определи тип связи:
             - Ассоциация (`--`) для простого отношения,
             - Наследование (`--|>`) для иерархий,
             - Композиция (`*--`) для отношения Части и Целого,
             - Использование (`..>`) для взаимодействия пользователя с GUI,
             - Реализация (`..|>`) для связи сущностей с интерфейсами.
           - Укажи кратность там, где это применимо (`1`, `*`, `0..*`).
           - Между двумя классами может быть только одна связь.
           - У ассоциации самый слабый приоритет — сначала попробуй выбрать другой тип отношения.

        5. **Добавление атрибутов и методов**
           - Для каждого класса:
             - Атрибуты → существительные с уточняющими прилагательными (например, `Название: String`),
             - Методы → глаголы из нарратива (например, `ПодтвердитьВвод()`).
           - Проверь соответствие методов классам (например, GUI-классы содержат методы реакций на действия).

        6. **Оценка сложности**
           - Рассчитай `C = 2^N`, где `N` — количество элементов фичи (классы без связей).

        ---

        **2. Правила написания PlantUML-кода для модели**

        ```plantuml
        @startuml domain_model
        ' Заголовок
            Title Название фичи
            skinparam WrapWidth 150

        ' **Классы и объекты**
            class "Имя класса" as alias {
                + {field} атрибут: Тип
                + {method} Метод()
            }
            object "Конкретный объект" as objectAlias
            класс --|> родительский_класс

        ' **GUI-элементы**
            class "Модальное окно" as modal << (U, #d310e4) GUI >>

        ' **Интерфейсы и сущности**
            interface "API сервис" as apiService
            entity "Файл" as fileEntity

        ' **Отношения**
            Класс1 "1" --* "0..*" Класс2 : композиция
            Объект ..> GUI_класс : использование
            entity ..|> interface : реализация

        ' **Стиль и комментарии**
            - Не используй left to right direction!
            - Длина линий: "--" для 5-12 классов, "---" для больших диаграмм.
            - Комментарии пиши на отдельных строках с апострофом.
        @enduml
        ```

        **Обязательные правила:**
        - **Синтаксис:**
          - Типы классов: `class`, `object`, `interface`, `entity`, `<<GUI>>`.
          - Методы начинаются с `+ {method}`, атрибуты — `+ {field}`.
          - Регистр: PascalCase для классов, camelCase для методов/атрибутов.

        - **Связи:**
          - Наследование только между классами одного типа (например, `class --|> class`).
          - GUI-классы связываются композицией только с другими GUI-элементами.
          - Пользователи используют GUI.

        - **Оформление:**
          - Максимум 12 классов на диаграмме (иначе требуется декомпозиция).
          - Для GUI-элементов используй стереотип `<< (U, #d310e4) GUI >>`.
          - Не указывай кратность, если она неочевидна.

        - **Проверки:**
          - Каждый метод соответствует контексту класса (например, `НазначитьРодительскуюПапку()` только у `Файл`).
          - Все связи из нарратива отражены на диаграмме.

        **Пример корректной записи:**
        ```plantuml
        class "Файл" as file {
            + {field} id: UUID
            + {method} НазначитьРодительскуюПапку()
        }
        file "1" <|.. "0..*" folder : реализация
        ```
        """;

    public String generateIconixPlantUml(String narrative, String ragContext) {
        String userPromptRaw = String.format("""
            НАРРАТИВ:
            %s

            ДОПОЛНИТЕЛЬНЫЙ КОНТЕКСТ (из RAG):
            %s

            Сгенерируй PlantUML доменной модели ICONIX по правилам выше.
            Выведи ТОЛЬКО один законченный блок:
            1) начинается строкой "@startuml" и заканчивается "@enduml";
            2) не добавляй никаких пояснений вне блока;
            3) максимум 12 классов/объектов/интерфейсов/сущностей на диаграмме.
            """, narrative, normalizeContext(ragContext));

        return chat.prompt()
                .system(PromptUtils.stEscape(ICONIX_SYSTEM_PROMPT))
                .user(PromptUtils.stEscape(userPromptRaw))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    public String refineModelWithIssues(String narrative, String currentPlantUml, List<Issue> issues, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (issues == null || issues.isEmpty()) {
            sb.append("нет");
        } else {
            for (Issue i : issues) {
                sb.append("- ").append(i.title()).append(" ⇒ ").append(i.suggestion())
                        .append(" (severity=").append(i.severity()).append(")").append("\n");
            }
        }

        String userPromptRaw = String.format("""
            НАРРАТИВ:
            %s

            ТЕКУЩАЯ МОДЕЛЬ (PlantUML):
            %s

            ЗАМЕЧАНИЯ ДЛЯ ПРАВКИ:
            %s

            ДОПОЛНИТЕЛЬНЫЙ КОНТЕКСТ (из RAG):
            %s

            Обнови модель, строго следуя ранее указанным правилам, и выведи ТОЛЬКО один блок PlantUML.
            Если какое-то замечание неуместно — игнорируй его молча.
            Максимум 12 элементов на диаграмме.
            """, narrative, currentPlantUml, sb.toString(), normalizeContext(ragContext));

        return chat.prompt()
                .system(PromptUtils.stEscape(ICONIX_SYSTEM_PROMPT))
                .user(PromptUtils.stEscape(userPromptRaw))
                .options(OpenAiChatOptions.builder()
                        .temperature(1.0)
                        .build())
                .call()
                .content();
    }

    /**
     * Экранирует фигурные скобки для StringTemplate (ST4), чтобы {field}, {method}, {user}, {editor}
     * и любые другие литеральные {...} не воспринимались как плейсхолдеры шаблона.
     */
    private static String normalizeContext(String ragContext) {
        return (ragContext == null || ragContext.isBlank()) ? "нет" : ragContext;
        }

}

