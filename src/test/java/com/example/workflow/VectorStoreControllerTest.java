package com.example.workflow;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import com.example.portal.shared.service.LocalVectorStoreService;
import com.example.portal.shared.service.VectorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для VectorStoreController.
 * Проверка admin-only доступа к GET documents, POST add, POST batch, DELETE.
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreControllerTest {

    @Mock
    private VectorizationService vectorizationService;

    @Mock
    private LocalVectorStoreService vectorStoreService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VectorStoreController vectorStoreController;

    private User adminUser;
    private User regularUser;
    private UUID testDocId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .password("hashed")
                .enabled(true)
                .isAdmin(true)
                .createdAt(Instant.now())
                .build();

        regularUser = User.builder()
                .id(UUID.randomUUID())
                .username("user")
                .password("hashed")
                .enabled(true)
                .isAdmin(false)
                .createdAt(Instant.now())
                .build();

        testDocId = UUID.randomUUID();
    }

    private void setAuthenticatedUser(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("GET /documents — админ получает список (200)")
    void getDocuments_admin_returnsOk() {
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(vectorStoreService.listDocuments(0, 20)).thenReturn(List.of(
                new LocalVectorStoreService.DocumentListItem(testDocId, "preview", Map.of(), Instant.now())
        ));
        when(vectorStoreService.countDocuments()).thenReturn(1L);

        ResponseEntity<?> response = vectorStoreController.getDocuments(0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("documents")).isNotNull();
        assertThat(body.get("total")).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /documents — обычный пользователь получает 403")
    void getDocuments_regularUser_returns403() {
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        ResponseEntity<?> response = vectorStoreController.getDocuments(0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(vectorStoreService, never()).listDocuments(anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /documents — неаутентифицированный получает 403")
    void getDocuments_unauthenticated_returns403() {
        ResponseEntity<?> response = vectorStoreController.getDocuments(0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /documents — админ успешно добавляет (200)")
    void addDocument_admin_returnsOk() {
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(vectorizationService.addDocument(anyString(), any())).thenReturn(testDocId);

        var request = new VectorStoreController.AddDocumentRequest("test content", Map.of());
        ResponseEntity<?> response = vectorStoreController.addDocument(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("id")).isEqualTo(testDocId.toString());
    }

    @Test
    @DisplayName("POST /documents — обычный пользователь получает 403")
    void addDocument_regularUser_returns403() {
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        var request = new VectorStoreController.AddDocumentRequest("test", null);
        ResponseEntity<?> response = vectorStoreController.addDocument(request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(vectorizationService, never()).addDocument(anyString(), any());
    }

    @Test
    @DisplayName("POST /documents/batch — админ успешно добавляет (200)")
    void addDocumentsBatch_admin_returnsOk() {
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(vectorizationService.addDocuments(List.of("a", "b"))).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        var request = new VectorStoreController.BatchAddRequest(List.of("a", "b"));
        ResponseEntity<?> response = vectorStoreController.addDocumentsBatch(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("count")).isEqualTo(2);
    }

    @Test
    @DisplayName("POST /documents/batch — обычный пользователь получает 403")
    void addDocumentsBatch_regularUser_returns403() {
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        var request = new VectorStoreController.BatchAddRequest(List.of("a"));
        ResponseEntity<?> response = vectorStoreController.addDocumentsBatch(request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(vectorizationService, never()).addDocuments(anyList());
    }

    @Test
    @DisplayName("DELETE /documents/{id} — админ успешно удаляет (200)")
    void deleteDocument_admin_returnsOk() {
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        ResponseEntity<?> response = vectorStoreController.deleteDocument(testDocId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(vectorStoreService).deleteDocument(testDocId);
    }

    @Test
    @DisplayName("DELETE /documents/{id} — обычный пользователь получает 403")
    void deleteDocument_regularUser_returns403() {
        setAuthenticatedUser("user", "ROLE_USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(regularUser));

        ResponseEntity<?> response = vectorStoreController.deleteDocument(testDocId);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(vectorStoreService, never()).deleteDocument(any());
    }

    @Test
    @DisplayName("POST /documents/upload — админ успешно загружает файлы (200)")
    void uploadDocuments_admin_returnsOk() throws Exception {
        setAuthenticatedUser("admin", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("file content".getBytes(StandardCharsets.UTF_8)));
        when(vectorizationService.uploadFromStream(eq("test.txt"), any(InputStream.class)))
                .thenReturn(List.of(testDocId));

        ResponseEntity<?> response = vectorStoreController.uploadDocuments(new MultipartFile[]{file});

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(vectorizationService).uploadFromStream(eq("test.txt"), any(InputStream.class));
    }
}
