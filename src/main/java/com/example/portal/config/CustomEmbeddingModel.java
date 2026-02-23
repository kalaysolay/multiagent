package com.example.portal.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EmbeddingModel с форматом запроса: {"inputs": ["..."], "model": "...", "encoding_format": "float"}.
 * Совместим с Hugging Face Inference API и похожими сервисами.
 */
@Slf4j
public class CustomEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomEmbeddingModel(String baseUrl, String model, String apiKey) {
        String url = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:11434";
        this.model = model != null ? model : "Salesforce/SFR-Embedding-Code-400M_R";

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.restClient = builder.build();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request != null && request.getInstructions() != null
                ? request.getInstructions()
                : List.of();
        List<float[]> embeddings = embedBatch(texts);
        List<Embedding> embeddingList = new java.util.ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            embeddingList.add(new Embedding(embeddings.get(i), i));
        }
        return new EmbeddingResponse(embeddingList);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document != null ? document.getText() : "");
    }

    private List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        // Убираем null и пустые строки — API возвращает "inputs cannot be empty"
        List<String> nonBlank = texts.stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();
        if (nonBlank.isEmpty()) {
            log.debug("All embedding inputs are empty or blank, skipping API call");
            return List.of();
        }
        RequestBody body = new RequestBody(nonBlank, model, "float");
        String response = restClient.post()
                .uri("")
                .body(body)
                .retrieve()
                .body(String.class);

        List<float[]> fromApi = parseResponse(response, nonBlank.size());
        // Если часть входов была пустой — восстанавливаем порядок (пустой вектор для blank)
        if (fromApi.size() == texts.size()) {
            return fromApi;
        }
        int idx = 0;
        List<float[]> result = new java.util.ArrayList<>(texts.size());
        for (String t : texts) {
            if (t != null && !t.isBlank()) {
                result.add(idx < fromApi.size() ? fromApi.get(idx++) : new float[0]);
            } else {
                result.add(new float[0]);
            }
        }
        return result;
    }

    private List<float[]> parseResponse(String json, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(json);
            // Вариант 1: [[0.1, 0.2, ...], ...] — массив массивов
            if (root.isArray()) {
                return parseEmbeddingsArray(root);
            }
            // Вариант 2: {"embeddings": [[...], ...]}
            JsonNode embeddings = root.get("embeddings");
            if (embeddings != null && embeddings.isArray()) {
                return parseEmbeddingsArray(embeddings);
            }
            // Вариант 3: {"data": [{"embedding": [...]}, ...]} — OpenAI-style
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                return data.findValues("embedding").stream()
                        .map(this::toFloatArray)
                        .collect(Collectors.toList());
            }
            throw new IllegalStateException("Unsupported embedding response format: " + (json != null && json.length() > 200 ? json.substring(0, 200) + "..." : json));
        } catch (Exception e) {
            log.error("Failed to parse embedding response", e);
            throw new RuntimeException("Failed to parse embedding response", e);
        }
    }

    private List<float[]> parseEmbeddingsArray(JsonNode arr) {
        List<float[]> result = new java.util.ArrayList<>();
        for (JsonNode item : arr) {
            result.add(toFloatArray(item));
        }
        return result;
    }

    private float[] toFloatArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new float[0];
        }
        float[] out = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
            out[i] = (float) node.get(i).asDouble();
        }
        return out;
    }

    private static class RequestBody {
        @JsonProperty("inputs")
        public List<String> inputs;
        @JsonProperty("model")
        public String model;
        @JsonProperty("encoding_format")
        public String encodingFormat;

        public RequestBody(List<String> inputs, String model, String encodingFormat) {
            this.inputs = inputs != null ? inputs : Collections.emptyList();
            this.model = model;
            this.encodingFormat = encodingFormat;
        }
    }
}
