package com.example.portal.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для VectorizationService.
 */
@ExtendWith(MockitoExtension.class)
class VectorizationServiceTest {

    @Mock
    private LocalVectorStoreService vectorStoreService;

    private VectorizationService vectorizationService;

    @BeforeEach
    void setUp() {
        vectorizationService = new VectorizationService(vectorStoreService);
    }

    @Test
    @DisplayName("addDocument делегирует в LocalVectorStoreService")
    void addDocument_delegatesToVectorStore() {
        UUID expectedId = UUID.randomUUID();
        when(vectorStoreService.addDocument(anyString(), any())).thenReturn(expectedId);

        UUID result = vectorizationService.addDocument("test content", Map.of("key", "value"));

        assertThat(result).isEqualTo(expectedId);
        verify(vectorStoreService).addDocument("test content", Map.of("key", "value"));
    }

    @Test
    @DisplayName("addDocuments делегирует в LocalVectorStoreService")
    void addDocuments_delegatesToVectorStore() {
        List<String> docs = List.of("a", "b");
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(vectorStoreService.addDocuments(docs)).thenReturn(ids);

        List<UUID> result = vectorizationService.addDocuments(docs);

        assertThat(result).isEqualTo(ids);
        verify(vectorStoreService).addDocuments(docs);
    }

    @Test
    @DisplayName("chunkText разбивает текст на чанки с перекрытием")
    void chunkText_splitsWithOverlap() {
        String text = "a".repeat(100);
        List<String> chunks = VectorizationService.chunkText(text, 30, 5);

        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0)).hasSize(30);
        assertThat(chunks.get(1)).hasSize(30);
        assertThat(chunks.get(3)).endsWith("a");
    }

    @Test
    @DisplayName("chunkText для пустого текста возвращает пустой список")
    void chunkText_emptyReturnsEmpty() {
        assertThat(VectorizationService.chunkText(null, 10, 2)).isEmpty();
        assertThat(VectorizationService.chunkText("", 10, 2)).isEmpty();
    }

    @Test
    @DisplayName("chunkText для короткого текста возвращает один чанк")
    void chunkText_shortText_returnsOneChunk() {
        List<String> chunks = VectorizationService.chunkText("hello", 100, 10);
        assertThat(chunks).containsExactly("hello");
    }

    @Test
    @DisplayName("uploadFromFile разбивает на чанки и добавляет с metadata source и chunk")
    void uploadFromFile_chunksAndAddsWithMetadata() {
        String content = "x".repeat(3000); // Больше одного чанка (2500)
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(vectorStoreService.addDocument(anyString(), anyMap())).thenReturn(id1, id2);

        List<UUID> result = vectorizationService.uploadFromFile("test.txt", content);

        assertThat(result).hasSize(2).containsExactly(id1, id2);

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStoreService, times(2)).addDocument(anyString(), metadataCaptor.capture());

        List<Map<String, Object>> metadatas = metadataCaptor.getAllValues();
        assertThat(metadatas.get(0)).containsEntry("source", "test.txt").containsEntry("chunk", 0);
        assertThat(metadatas.get(1)).containsEntry("source", "test.txt").containsEntry("chunk", 1);
    }

    @Test
    @DisplayName("uploadFromFile для пустого контента возвращает пустой список")
    void uploadFromFile_emptyContent_returnsEmpty() {
        List<UUID> result = vectorizationService.uploadFromFile("empty.txt", "");

        assertThat(result).isEmpty();
        verify(vectorStoreService, never()).addDocument(anyString(), any());
    }
}
