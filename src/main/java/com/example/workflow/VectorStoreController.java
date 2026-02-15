package com.example.workflow;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import com.example.portal.shared.service.LocalVectorStoreService;
import com.example.portal.shared.service.VectorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST контроллер для управления локальным векторным хранилищем.
 * <p>
 * Векторизация (добавление документов) — через VectorizationService.
 * Поиск, список, удаление — через LocalVectorStoreService.
 * </p>
 */
@RestController
@RequestMapping("/api/vector-store")
@RequiredArgsConstructor
@Slf4j
public class VectorStoreController {

    private final VectorizationService vectorizationService;
    private final LocalVectorStoreService vectorStoreService;
    private final UserRepository userRepository;
    
    /**
     * Получить список документов с пагинацией.
     * GET /api/vector-store/documents?page=0&size=20
     * Только для администраторов.
     */
    @GetMapping("/documents")
    public ResponseEntity<?> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }
        try {
            int offset = page * size;
            List<LocalVectorStoreService.DocumentListItem> documents = vectorStoreService.listDocuments(offset, size);
            long total = vectorStoreService.countDocuments();
            List<Map<String, Object>> docsList = documents.stream()
                    .map(d -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", d.id().toString());
                        m.put("contentPreview", d.contentPreview());
                        m.put("metadata", d.metadata() != null ? d.metadata() : Map.of());
                        m.put("createdAt", d.createdAt() != null ? d.createdAt().toString() : null);
                        return m;
                    })
                    .toList();
            return ResponseEntity.ok(Map.of("documents", docsList, "total", total));
        } catch (Exception e) {
            log.error("Failed to list documents", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to list documents: " + e.getMessage()));
        }
    }

    /**
     * Добавить документ в векторное хранилище.
     * POST /api/vector-store/documents
     * Body: { "content": "текст документа", "metadata": { "key": "value" } }
     * Только для администраторов.
     */
    @PostMapping("/documents")
    public ResponseEntity<?> addDocument(@RequestBody AddDocumentRequest request) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }
        try {
            UUID id = vectorizationService.addDocument(request.content(), request.metadata());
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
     * POST /api/vector-store/documents/batch
     * Body: { "documents": ["текст1", "текст2", ...] }
     * Только для администраторов.
     */
    @PostMapping("/documents/batch")
    public ResponseEntity<?> addDocumentsBatch(@RequestBody BatchAddRequest request) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }
        try {
            List<UUID> ids = vectorizationService.addDocuments(request.documents());
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
     * Загрузить файлы в векторное хранилище.
     * POST /api/vector-store/documents/upload
     * Multipart: file[] — массив файлов. Каждый файл разбивается на чанки ~2500 символов.
     * Только для администраторов.
     */
    @PostMapping("/documents/upload")
    public ResponseEntity<?> uploadDocuments(@RequestParam("files") MultipartFile[] files) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No files provided"));
        }
        try {
            List<UUID> ids = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                List<UUID> chunkIds = vectorizationService.uploadFromFile(filename, content);
                ids.addAll(chunkIds);
            }
            return ResponseEntity.ok(Map.of(
                    "ids", ids.stream().map(UUID::toString).toList(),
                    "count", ids.size(),
                    "message", "Files uploaded and vectorized successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to upload documents", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload documents: " + e.getMessage()));
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
     * DELETE /api/vector-store/documents/{id}
     * Только для администраторов.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable UUID id) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Доступ запрещён. Требуются права администратора."));
        }
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

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    // DTOs для запросов
    public record AddDocumentRequest(String content, Map<String, Object> metadata) {}
    public record BatchAddRequest(List<String> documents) {}
    public record SearchRequest(String query, Integer topK) {}
}

