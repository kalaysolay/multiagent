package com.example.workflow;

import com.example.portal.shared.service.AsciiDocRenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Контроллер для рендеринга AsciiDoc документов в HTML.
 */
@RestController
@RequestMapping("/render")
@RequiredArgsConstructor
public class AsciiDocRenderController {
    
    private final AsciiDocRenderService renderService;
    
    /**
     * Рендерит AsciiDoc документ в HTML.
     * 
     * POST /render/adoc
     * Body: { "asciiDoc": "= Заголовок\n\nТекст..." }
     */
    @PostMapping("/adoc")
    public ResponseEntity<?> renderToHtml(@RequestBody Map<String, String> request) {
        String asciiDocContent = request.get("asciiDoc");
        
        if (asciiDocContent == null || asciiDocContent.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "AsciiDoc содержимое не может быть пустым"));
        }
        
        try {
            String html = renderService.renderToHtml(asciiDocContent);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("html", html));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка при рендеринге: " + e.getMessage()));
        }
    }
    
    /**
     * Валидирует синтаксис AsciiDoc документа.
     * 
     * POST /render/adoc/validate
     * Body: { "asciiDoc": "= Заголовок\n\nТекст..." }
     */
    @PostMapping("/adoc/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> request) {
        String asciiDocContent = request.get("asciiDoc");
        
        if (asciiDocContent == null || asciiDocContent.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "AsciiDoc содержимое не может быть пустым"));
        }
        
        try {
            boolean isValid = renderService.validate(asciiDocContent);
            if (isValid) {
                return ResponseEntity.ok()
                        .body(Map.of("valid", true));
            } else {
                return ResponseEntity.ok()
                        .body(Map.of("valid", false, "error", "Обнаружены ошибки в синтаксисе AsciiDoc"));
            }
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .body(Map.of("valid", false, "error", "Ошибка валидации: " + e.getMessage()));
        }
    }
}

