package com.example.portal.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для LocalVectorStoreService.
 * EmbeddingModel мокается — эмбеддинги не вызывают реальный Ollama.
 */
@ExtendWith(MockitoExtension.class)
class LocalVectorStoreServiceTest {

    private static final float[] MOCK_EMBEDDING_768 = new float[768];

    static {
        for (int i = 0; i < 768; i++) {
            MOCK_EMBEDDING_768[i] = 0.01f * (i % 100);
        }
    }

    @Mock
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private LocalVectorStoreService localVectorStoreService;

    @BeforeEach
    void setUp() {
        lenient().when(embeddingModel.embed(anyString())).thenReturn(MOCK_EMBEDDING_768);
        localVectorStoreService = new LocalVectorStoreService(jdbcTemplate, embeddingModel, "document_embeddings");
    }

    @Test
    @DisplayName("addDocument создаёт эмбеддинг и вставляет в БД")
    void addDocument_createsEmbeddingAndInserts() {
        UUID id = localVectorStoreService.addDocument("test", Map.of("source", "file.txt"));

        verify(embeddingModel).embed("test");
        verify(jdbcTemplate).update(
                contains("INSERT INTO"),
                any(UUID.class),
                eq("test"),
                anyString(),
                any()
        );
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("findSimilar создаёт эмбеддинг запроса и выполняет поиск")
    void findSimilar_createsQueryEmbeddingAndSearches() {
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class))).thenReturn(List.of(
                new LocalVectorStoreService.DocumentResult(UUID.randomUUID(), "found", 0.9, Map.of())
        ));

        List<LocalVectorStoreService.DocumentResult> results = localVectorStoreService.findSimilar("query", 5);

        verify(embeddingModel).embed("query");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("found");
        assertThat(results.get(0).similarity()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("listDocuments возвращает список без вызова embedding")
    void listDocuments_noEmbeddingCall() {
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class))).thenReturn(List.of(
                new LocalVectorStoreService.DocumentListItem(UUID.randomUUID(), "preview", Map.of(), Instant.now())
        ));

        List<LocalVectorStoreService.DocumentListItem> docs = localVectorStoreService.listDocuments(0, 10);

        verify(embeddingModel, never()).embed(anyString());
        assertThat(docs).hasSize(1);
    }

    @Test
    @DisplayName("countDocuments возвращает количество")
    void countDocuments_returnsCount() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        long count = localVectorStoreService.countDocuments();

        assertThat(count).isEqualTo(42);
    }

    @Test
    @DisplayName("deleteDocument удаляет по id")
    void deleteDocument_deletesById() {
        UUID id = UUID.randomUUID();

        localVectorStoreService.deleteDocument(id);

        verify(jdbcTemplate).update(contains("DELETE FROM"), eq(id));
    }
}
