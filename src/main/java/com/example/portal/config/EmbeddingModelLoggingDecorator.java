package com.example.portal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Декоратор EmbeddingModel с логированием вызовов и выводом curl для отладки в Postman.
 */
@Slf4j
public class EmbeddingModelLoggingDecorator implements EmbeddingModel {

    private static final int MAX_CURL_TEXT_LENGTH = 200;

    private final EmbeddingModel delegate;
    private final String baseUrl;
    private final String model;
    private final String apiKeyEnvVar;

    public EmbeddingModelLoggingDecorator(EmbeddingModel delegate, String baseUrl, String model, String apiKeyEnvVar) {
        this.delegate = delegate;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:11434";
        this.model = model != null ? model : "nomic-embed-text";
        this.apiKeyEnvVar = apiKeyEnvVar;
    }

    @Override
    public float[] embed(String text) {
        log.info("EmbeddingModel: вызов embed(), длина текста={}", text != null ? text.length() : 0);
        log.info("EmbeddingModel: curl для Postman (скопируйте и выполните):\n{}", buildOllamaCurl(text));

        try {
            float[] result = delegate.embed(text);
            log.info("EmbeddingModel: успех, размерность={}", result != null ? result.length : 0);
            return result;
        } catch (Exception e) {
            log.error("EmbeddingModel: ошибка при вызове embed() - {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request != null && request.getInstructions() != null ? request.getInstructions() : List.of();
        log.info("EmbeddingModel: вызов call(), texts={}", texts.size());
        for (int i = 0; i < Math.min(texts.size(), 3); i++) {
            log.info("EmbeddingModel: curl для Postman (текст #{}):\n{}", i + 1, buildOllamaCurl(texts.get(i)));
        }
        if (texts.size() > 3) {
            log.info("EmbeddingModel: ... и ещё {} текстов", texts.size() - 3);
        }
        try {
            EmbeddingResponse result = delegate.call(request);
            log.info("EmbeddingModel: call() успех");
            return result;
        } catch (Exception e) {
            log.error("EmbeddingModel: ошибка при call() - {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public float[] embed(Document document) {
        String text = document != null && document.isText() ? document.getText() : "";
        log.info("EmbeddingModel: вызов embed(Document), длина текста={}", text.length());
        log.info("EmbeddingModel: curl для Postman (скопируйте и выполните):\n{}", buildOllamaCurl(text));

        try {
            float[] result = delegate.embed(document);
            log.info("EmbeddingModel: успех, размерность={}", result != null ? result.length : 0);
            return result;
        } catch (Exception e) {
            log.error("EmbeddingModel: ошибка при вызове embed(Document) - {}", e.getMessage(), e);
            throw e;
        }
    }

    private String buildOllamaCurl(String text) {
        String sample = text != null ? text : "";
        if (sample.length() > MAX_CURL_TEXT_LENGTH) {
            sample = sample.substring(0, MAX_CURL_TEXT_LENGTH) + "...";
        }
        String jsonInput = escapeJson(sample);
        String url = baseUrl.endsWith("/api/embed") || baseUrl.endsWith("/embed") ? baseUrl : baseUrl + "/api/embed";

        StringBuilder curl = new StringBuilder();
        curl.append("curl -X POST \"").append(url).append("\" \\\n");
        curl.append("  -H \"Content-Type: application/json\"");
        if (apiKeyEnvVar != null && !apiKeyEnvVar.isBlank()) {
            curl.append(" \\\n  -H \"Authorization: Bearer $").append(apiKeyEnvVar).append("\"");
        }
        curl.append(" \\\n");
        curl.append("  -d '{\"model\":\"").append(escapeForShell(model)).append("\",\"input\":\"").append(jsonInput).append("\"}'");
        return curl.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeForShell(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
