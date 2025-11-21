package com.example.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpenAiRagService {

    private final WebClient client;
    private final String vectorStoreId;

    public OpenAiRagService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${app.openai.vector-store-id:}") String vectorStoreId
    ) {
        this.client = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.vectorStoreId = (vectorStoreId == null || vectorStoreId.isBlank()) ? null : vectorStoreId;
    }

    /**
     * Возвращает конкатенированный текст из topK релевантных фрагментов.
     * Возвращает пустой результат, если векторное хранилище не задано или произошла ошибка.
     */
    public ContextResult retrieveContext(String query, int topK) {
        if (vectorStoreId == null) {
            return new ContextResult("", 0, false);
        }
        try {
            Map<?, ?> resp = client.post()
                    .uri("/vector_stores/{id}/search", vectorStoreId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "query", query
                           // "top_k", topK,
                           // "return_metadata", true
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) {
                return new ContextResult("", 0, true);
            }

            Object dataObj = resp.get("data");
            if (!(dataObj instanceof List<?> dataList)) {
                return new ContextResult("", 0, true);
            }

            List<String> fragments = new ArrayList<>();
            for (Object item : dataList) {
                if (item instanceof Map<?, ?> itemMap) {
                    fragments.add(extractText(itemMap));
                }
            }

            List<String> cleaned = fragments.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());

            String combined = cleaned.stream()
                    .collect(Collectors.joining("\n---\n"));

            return new ContextResult(combined, cleaned.size(), true);

        } catch (Exception e) {
            log.warn("Failed to retrieve RAG context: {}", e.getMessage());
            return new ContextResult("", 0, true);
        }
    }

    private String extractText(Map<?, ?> itemMap) {
        Object contentObj = itemMap.get("content");
        if (contentObj instanceof String str) {
            return str;
        }
        if (!(contentObj instanceof List<?> contentList)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object entry : contentList) {
            if (!(entry instanceof Map<?, ?> entryMap)) {
                continue;
            }
            Object textObj = entryMap.get("text");
            if (textObj instanceof String s) {
                sb.append(s);
            } else if (textObj instanceof List<?> textList) {
                for (Object textEntry : textList) {
                    if (textEntry instanceof Map<?, ?> textMap) {
                        Object inner = textMap.get("text");
                        if (inner instanceof String innerStr) {
                            sb.append(innerStr);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    public record ContextResult(String text, int fragmentsCount, boolean vectorStoreAvailable) {
        public String text() {
            return text == null ? "" : text;
        }
    }
}


