package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Реализация RAG (Retrieval-Augmented Generation) для локального векторного хранилища.
 * <p>
 * RAG позволяет LLM получать релевантный контекст из документов перед генерацией ответа.
 * Этот сервис ищет похожие фрагменты в pgvector по семантическому сходству и возвращает
 * их как контекст для подстановки в промпт.
 * </p>
 * <p>
 * Активируется при {@code app.vector-store-provider=LOCAL}.
 * </p>
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.vector-store-provider", havingValue = "LOCAL"
)
@Slf4j
public class LocalRagService implements RagService {
    
    private final LocalVectorStoreService vectorStoreService;
    
    public LocalRagService(LocalVectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }
    
    /**
     * Находит topK наиболее релевантных фрагментов по векторному сходству и объединяет их в один текст.
     */
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

