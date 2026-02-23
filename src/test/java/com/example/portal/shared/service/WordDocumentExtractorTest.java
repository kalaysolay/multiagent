package com.example.portal.shared.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тест извлечения текста из .docx через WordDocumentExtractor.
 */
class WordDocumentExtractorTest {

    private WordDocumentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new WordDocumentExtractor();
    }

    @Test
    @DisplayName("Из .docx извлекается текст параграфов")
    void extractText_docxWithParagraphs_returnsText() throws IOException {
        String expected = "Hello Word document for vectorization.";
        byte[] docxBytes = createMinimalDocx(expected);
        try (InputStream in = new ByteArrayInputStream(docxBytes)) {
            String result = extractor.extractText("test.docx", in);
            assertThat(result).contains(expected);
        }
    }

    @Test
    @DisplayName("Пустой поток возвращает пустую строку")
    void extractText_nullStream_returnsEmpty() throws IOException {
        String result = extractor.extractText("test.docx", null);
        assertThat(result).isEmpty();
    }

    private static byte[] createMinimalDocx(String paragraphText) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(paragraphText);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }
}
