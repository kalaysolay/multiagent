package com.example.portal.shared.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Извлечение текста из файла по имени и потоку.
 * Реализации — для .txt (plain UTF-8), .docx (Word), в будущем — код (.java, .py и т.д.).
 */
public interface DocumentTextExtractor {

    /**
     * Извлечь текст из потока файла.
     *
     * @param filename   имя файла (для выбора реализации по расширению)
     * @param inputStream поток содержимого файла (не закрывается вызывающим)
     * @return извлечённый текст; пустая строка при неподдерживаемом формате или ошибке парсинга
     * @throws IOException при ошибке чтения потока
     */
    String extractText(String filename, InputStream inputStream) throws IOException;
}
