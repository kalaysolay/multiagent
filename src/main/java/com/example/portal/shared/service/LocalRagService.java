package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Реализация RAG сервиса для локального векторного хранилища (PostgreSQL + pgvector).
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.vector-store-provider", havingValue = "DEEPSEEK"
)
@Slf4j
public class LocalRagService implements RagService {
    
    private final LocalVectorStoreService vectorStoreService;
    
    public LocalRagService(LocalVectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }
    
    @Override
    public ContextResult retrieveContext(String query, int topK) {
        try {
            List<LocalVectorStoreService.DocumentResult> results = vectorStoreService.findSimilar(query, topK);
            
            if (results.isEmpty()) {
                log.debug("No similar documents found for query");
                return new ContextResult("", 0, true);
            }
            
            List<String> fragments = results.stream()
                    .map(result -> result.content())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());
            
            String combined = fragments.stream()
                    .collect(Collectors.joining("\n---\n"));
            
            log.debug("Retrieved {} fragments from local vector store", fragments.size());
            return new ContextResult(combined, fragments.size(), true);
            
        } catch (Exception e) {
            log.warn("Failed to retrieve RAG context from local vector store: {}", e.getMessage());
            return new ContextResult("", 0, true);
        }
    }
}

