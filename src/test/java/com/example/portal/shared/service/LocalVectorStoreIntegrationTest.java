package com.example.portal.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Интеграционный тест: векторизация, сохранение и RAG-поиск через pgvector.
 * Использует Testcontainers (PostgreSQL + pgvector) и mock EmbeddingModel.
 * Требует Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LocalVectorStoreIntegrationTest {

    // V13: document_embeddings использует vector(1024)
    private static final float[] MOCK_EMBEDDING_1024 = new float[1024];

    static {
        for (int i = 0; i < 1024; i++) {
            MOCK_EMBEDDING_1024[i] = 0.001f * (i % 100);
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("iconix_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean(name = "ollamaEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private LocalVectorStoreService vectorStoreService;

    @Autowired
    private RagService ragService;

    @BeforeEach
    void setUp() {
        when(embeddingModel.embed(anyString())).thenReturn(MOCK_EMBEDDING_1024);
    }

    @Test
    @DisplayName("Векторизация compliance-automation и поиск возвращают релевантные фрагменты")
    void vectorizeAndSearch_complianceDocument_returnsRelevantFragments() throws Exception {
        String content = new ClassPathResource("compliance-automation.txt").getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).isNotBlank();

        List<java.util.UUID> ids = vectorizationService.uploadFromFile("compliance-automation.txt", content);
        assertThat(ids).isNotEmpty();

        long count = vectorStoreService.countDocuments();
        assertThat(count).isEqualTo(ids.size());

        List<LocalVectorStoreService.DocumentResult> results = vectorStoreService.findSimilar("Какие роли у пользователей?", 5);
        assertThat(results).isNotEmpty();
        String combinedContent = results.stream().map(LocalVectorStoreService.DocumentResult::content).reduce("", (a, b) -> a + " " + b);
        assertThat(combinedContent).containsAnyOf("Employee", "Officer", "Admin", "роли", "JWT", "комплаенс");
    }

    @Test
    @DisplayName("RAG retrieveContext возвращает непустой контекст для запроса по JWT")
    void ragRetrieveContext_jwtQuery_returnsContext() throws Exception {
        String content = new ClassPathResource("compliance-automation.txt").getContentAsString(StandardCharsets.UTF_8);
        vectorizationService.uploadFromFile("compliance-automation.txt", content);

        RagService.ContextResult ctx = ragService.retrieveContext("JWT время жизни токена", 3);
        assertThat(ctx.vectorStoreAvailable()).isTrue();
        assertThat(ctx.text()).isNotBlank();
        assertThat(ctx.fragmentsCount()).isGreaterThan(0);
    }
}
