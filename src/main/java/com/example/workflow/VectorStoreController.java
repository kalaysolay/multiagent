package com.example.workflow;

import com.example.portal.shared.service.LocalVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST контроллер для управления локальным векторным хранилищем.
 * Позволяет добавлять, удалять и искать документы в векторном хранилище.
 */
@RestController
@RequestMapping("/api/vector-store")
@RequiredArgsConstructor
@Slf4j
public class VectorStoreController {
    
    private final LocalVectorStoreService vectorStoreService;
    
    /**
     * Добавить документ в векторное хранилище.
     * 
     * POST /api/vector-store/documents
     * Body: { "content": "текст документа", "metadata": { "key": "value" } }
     */
    @PostMapping("/documents")
    public ResponseEntity<?> addDocument(@RequestBody AddDocumentRequest request) {
        try {
            UUID id = vectorStoreService.addDocument(request.content(), request.metadata());
            return ResponseEntity.ok(Map.of(
                    "id", id.toString(),
                    "message", "Document added successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to add document", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to add document: " + e.getMessage()));
        }
    }
    
    /**
     * Добавить несколько документов пакетом.
     * 
     * POST /api/vector-store/documents/batch
     * Body: { "documents": ["текст1", "текст2", ...] }
     */
    @PostMapping("/documents/batch")
    public ResponseEntity<?> addDocumentsBatch(@RequestBody BatchAddRequest request) {
        try {
            List<UUID> ids = vectorStoreService.addDocuments(request.documents());
            return ResponseEntity.ok(Map.of(
                    "ids", ids.stream().map(UUID::toString).toList(),
                    "count", ids.size(),
                    "message", "Documents added successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to add documents batch", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to add documents: " + e.getMessage()));
        }
    }
    
    /**
     * Найти похожие документы по запросу.
     * 
     * POST /api/vector-store/search
     * Body: { "query": "текст запроса", "topK": 5 }
     */
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest request) {
        try {
            List<LocalVectorStoreService.DocumentResult> results = 
                    vectorStoreService.findSimilar(request.query(), request.topK() != null ? request.topK() : 5);
            
            List<Map<String, Object>> resultsList = results.stream()
                    .map(result -> Map.of(
                            "id", result.id().toString(),
                            "content", result.content(),
                            "similarity", result.similarity(),
                            "metadata", result.metadata() != null ? result.metadata() : Map.of()
                    ))
                    .toList();
            
            return ResponseEntity.ok(Map.of(
                    "results", resultsList,
                    "count", resultsList.size()
            ));
        } catch (Exception e) {
            log.error("Failed to search documents", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to search documents: " + e.getMessage()));
        }
    }
    
    /**
     * Удалить документ по ID.
     * 
     * DELETE /api/vector-store/documents/{id}
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable UUID id) {
        try {
            vectorStoreService.deleteDocument(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Document deleted successfully",
                    "id", id.toString()
            ));
        } catch (Exception e) {
            log.error("Failed to delete document", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete document: " + e.getMessage()));
        }
    }
    
    // DTOs для запросов
    public record AddDocumentRequest(String content, Map<String, Object> metadata) {}
    public record BatchAddRequest(List<String> documents) {}
    public record SearchRequest(String query, Integer topK) {}
}

