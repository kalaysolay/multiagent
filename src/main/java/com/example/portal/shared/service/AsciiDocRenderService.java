package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для рендеринга AsciiDoc документов в HTML.
 */
@Slf4j
@Service
public class AsciiDocRenderService {
    
    private final Asciidoctor asciidoctor;
    
    public AsciiDocRenderService() {
        this.asciidoctor = Asciidoctor.Factory.create();
    }
    
    /**
     * Рендерит AsciiDoc документ в HTML.
     * 
     * @param asciiDocContent Содержимое AsciiDoc документа
     * @return HTML представление документа
     */
    public String renderToHtml(String asciiDocContent) {
        if (asciiDocContent == null || asciiDocContent.isBlank()) {
            throw new IllegalArgumentException("AsciiDoc content cannot be empty");
        }
        
        try {
            // Создаем опции для рендеринга
            // SafeMode.SAFE соответствует уровню 1
            // В Asciidoctor Java API SafeMode передается как Integer
            Map<String, Object> options = new HashMap<>();
            options.put(Options.SAFE, 1); // 1 = SafeMode.SAFE
            options.put(Options.BACKEND, "html5");
            options.put(Options.IN_PLACE, false);
            options.put(Options.STANDALONE, true);
            
            // Включаем поддержку PlantUML диаграмм и другие атрибуты
            // Убираем TOC слева, так как он мешает прокрутке в модальном окне
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("source-highlighter", "highlight.js");
            attributes.put("toc", ""); // Убираем TOC
            attributes.put("icons", "font");
            options.put(Options.ATTRIBUTES, attributes);
            
            String html = asciidoctor.convert(asciiDocContent, options);
            
            log.info("Rendered AsciiDoc to HTML: {} chars -> {} chars", 
                    asciiDocContent.length(), html.length());
            
            return html;
            
        } catch (Exception e) {
            log.error("Error rendering AsciiDoc to HTML", e);
            throw new RuntimeException("Failed to render AsciiDoc: " + e.getMessage(), e);
        }
    }
    
    /**
     * Валидирует синтаксис AsciiDoc документа.
     * 
     * @param asciiDocContent Содержимое AsciiDoc документа
     * @return true, если документ валиден
     */
    public boolean validate(String asciiDocContent) {
        if (asciiDocContent == null || asciiDocContent.isBlank()) {
            return false;
        }
        
        try {
            // Пробуем загрузить документ
            asciidoctor.load(asciiDocContent, new HashMap<>());
            return true;
        } catch (Exception e) {
            log.warn("AsciiDoc validation failed", e);
            return false;
        }
    }
}

