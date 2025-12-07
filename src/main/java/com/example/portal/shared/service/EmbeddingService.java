package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сервис для создания векторных эмбеддингов из текста.
 * Использует OpenAI Embeddings API напрямую через WebClient.
 */
@Service
@Slf4j
public class EmbeddingService {
    
    private final WebClient client;
    private static final String EMBEDDING_MODEL = "text-embedding-ada-002";
    private static final int EMBEDDING_DIMENSIONS = 1536;
    
    public EmbeddingService(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.client = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
    
    /**
     * Создать эмбеддинг для одного текста.
     * 
     * @param text текст для векторизации
     * @return векторное представление текста
     */
    public List<Double> createEmbedding(String text) {
        try {
            Map<?, ?> response = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", EMBEDDING_MODEL,
                            "input", text
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response == null) {
                throw new RuntimeException("Empty response from OpenAI embeddings API");
            }
            
            Object dataObj = response.get("data");
            if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
                throw new RuntimeException("Invalid response format from OpenAI embeddings API");
            }
            
            Object firstItem = dataList.get(0);
            if (!(firstItem instanceof Map<?, ?> itemMap)) {
                throw new RuntimeException("Invalid item format in embeddings response");
            }
            
            Object embeddingObj = itemMap.get("embedding");
            if (!(embeddingObj instanceof List<?> embeddingList)) {
                throw new RuntimeException("Invalid embedding format in response");
            }
            
            List<Double> embedding = new ArrayList<>();
            for (Object value : embeddingList) {
                if (value instanceof Number num) {
                    embedding.add(num.doubleValue());
                } else {
                    throw new RuntimeException("Invalid embedding value type: " + value.getClass());
                }
            }
            
            log.debug("Created embedding with {} dimensions", embedding.size());
            return embedding;
            
        } catch (Exception e) {
            log.error("Failed to create embedding for text: {}", 
                    text != null && text.length() > 100 ? text.substring(0, 100) : text, e);
            throw new RuntimeException("Failed to create embedding", e);
        }
    }
    
    /**
     * Создать эмбеддинги для списка текстов.
     * 
     * @param texts список текстов для векторизации
     * @return список векторных представлений
     */
    public List<List<Double>> createEmbeddings(List<String> texts) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(createEmbedding(text));
        }
        return embeddings;
    }
    
    /**
     * Получить размерность векторов (количество измерений).
     * 
     * @return размерность вектора
     */
    public int getDimensions() {
        return EMBEDDING_DIMENSIONS;
    }
}

