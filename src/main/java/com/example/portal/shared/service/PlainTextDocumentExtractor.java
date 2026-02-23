package com.example.portal.shared.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Извлечение текста как plain UTF-8 (fallback для .txt и неизвестных расширений).
 */
@Component
public class PlainTextDocumentExtractor implements DocumentTextExtractor {

    @Override
    public String extractText(String filename, InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
