package com.example.workflow;

import com.example.workflow.DocumentationGenerateService.FileEntry;
import com.example.workflow.DocumentationGenerateService.FolderAlreadyExistsException;
import com.example.workflow.DocumentationGenerateService.GenerateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API для генерации и просмотра ICONIX-документации по workflow-сессии.
 * Генерация создаёт папку с domain.puml, use_case.puml, scn_*.adoc, mvc_*.puml.
 * Просмотр отдаёт список файлов и содержимое выбранного файла.
 */
@RestController
@RequestMapping("/api/usecase/documentation")
@RequiredArgsConstructor
@Slf4j
public class DocumentationController {

    private final DocumentationGenerateService documentationService;

    /**
     * Сгенерировать документацию в новую папку.
     * POST /api/usecase/documentation/generate
     * Body: { "requestId": "...", "folderName": "feature-auth" }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest request) {
        if (request == null || request.requestId() == null || request.requestId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "requestId обязателен"));
        }
        if (request.folderName() == null || request.folderName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Название папки обязательно"));
        }

        try {
            GenerateResult result = documentationService.generate(request.requestId(), request.folderName().trim());
            return ResponseEntity.ok(Map.of(
                    "folderPath", result.folderPath(),
                    "createdFiles", result.createdFiles(),
                    "updated", result.updated()
            ));
        } catch (FolderAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Documentation generation failed for requestId={}", request.requestId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка генерации: " + e.getMessage()));
        }
    }

    /**
     * Проверить, есть ли сгенерированная документация для сессии.
     * GET /api/usecase/documentation/{requestId}/exists
     */
    @GetMapping("/{requestId}/exists")
    public ResponseEntity<Map<String, Boolean>> exists(@PathVariable String requestId) {
        try {
            boolean exists = documentationService.hasDocumentation(requestId);
            return ResponseEntity.ok(Map.of("exists", exists));
        } catch (Exception e) {
            log.warn("Error checking documentation exists for {}: {}", requestId, e.getMessage());
            return ResponseEntity.ok(Map.of("exists", false));
        }
    }

    /**
     * Список файлов в папке документации сессии.
     * GET /api/usecase/documentation/{requestId}/files
     */
    @GetMapping("/{requestId}/files")
    public ResponseEntity<?> listFiles(@PathVariable String requestId) {
        try {
            List<FileEntry> files = documentationService.listFiles(requestId);
            return ResponseEntity.ok(Map.of("files", files));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error listing documentation files for {}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Содержимое файла (plain text). Для .puml фронт может отрендерить диаграмму.
     * GET /api/usecase/documentation/{requestId}/files/{fileName}
     */
    @GetMapping(value = "/{requestId}/files/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> getFileContent(@PathVariable String requestId, @PathVariable String fileName) {
        try {
            String content = documentationService.readFileContent(requestId, fileName);
            return ResponseEntity.ok(content);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("не найден")) {
                return ResponseEntity.notFound().build();
            }
            log.warn("Error reading file {} for {}: {}", fileName, requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    public record GenerateRequest(String requestId, String folderName) {}
}
