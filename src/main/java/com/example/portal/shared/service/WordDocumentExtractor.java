package com.example.portal.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;

/**
 * Извлечение текста из .docx через Apache POI (XWPFDocument).
 */
@Component
@Slf4j
public class WordDocumentExtractor implements DocumentTextExtractor {

    @Override
    public String extractText(String filename, InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(text);
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String text = cell.getText();
                        if (text != null && !text.isEmpty()) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(text.trim());
                        }
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to extract text from Word document {}: {}", filename, e.getMessage());
            return "";
        }
    }
}
