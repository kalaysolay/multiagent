package com.example.portal.shared.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Фасад: выбор экстрактора по расширению файла.
 * .docx → WordDocumentExtractor, иначе → PlainTextDocumentExtractor.
 */
@Component
@Primary
public class DefaultDocumentTextExtractor implements DocumentTextExtractor {

    private final WordDocumentExtractor wordExtractor;
    private final PlainTextDocumentExtractor plainTextExtractor;

    public DefaultDocumentTextExtractor(WordDocumentExtractor wordExtractor,
                                        PlainTextDocumentExtractor plainTextExtractor) {
        this.wordExtractor = wordExtractor;
        this.plainTextExtractor = plainTextExtractor;
    }

    @Override
    public String extractText(String filename, InputStream inputStream) throws IOException {
        if (filename == null || inputStream == null) {
            return "";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".docx")) {
            return wordExtractor.extractText(filename, inputStream);
        }
        return plainTextExtractor.extractText(filename, inputStream);
    }
}
