package com.example.workflow;

import com.example.workflow.DocumentationGenerateService.FileEntry;
import com.example.workflow.DocumentationGenerateService.FolderAlreadyExistsException;
import com.example.workflow.DocumentationGenerateService.GenerateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты для DocumentationController: генерация, exists, list files, file content.
 */
@ExtendWith(MockitoExtension.class)
class DocumentationControllerTest {

    @Mock
    private DocumentationGenerateService documentationService;

    @InjectMocks
    private DocumentationController controller;

    private static final String REQUEST_ID = "req-123";

    @Test
    @DisplayName("generate — при успехе возвращает 200 и список созданных файлов")
    void generateReturns200AndCreatedFiles() {
        when(documentationService.generate(eq(REQUEST_ID), eq("my-docs")))
                .thenReturn(new GenerateResult("/path/my-docs", List.of("domain.puml", "use_case.puml")));

        ResponseEntity<?> res = controller.generate(new DocumentationController.GenerateRequest(REQUEST_ID, "my-docs"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertThat(body).containsKey("folderPath").containsKey("createdFiles");
        assertThat((List<?>) body.get("createdFiles")).hasSize(2);
    }

    @Test
    @DisplayName("generate — при существующей папке возвращает 409")
    void generateReturns409WhenFolderExists() {
        when(documentationService.generate(eq(REQUEST_ID), eq("existing")))
                .thenThrow(new FolderAlreadyExistsException("Каталог с таким именем уже существует: existing"));

        ResponseEntity<?> res = controller.generate(new DocumentationController.GenerateRequest(REQUEST_ID, "existing"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) res.getBody();
        assertThat(body.get("error")).contains("уже существует");
    }

    @Test
    @DisplayName("generate — при пустом requestId возвращает 400")
    void generateReturns400WhenRequestIdBlank() {
        ResponseEntity<?> res = controller.generate(new DocumentationController.GenerateRequest("", "folder"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("exists — возвращает 200 и exists: true/false")
    void existsReturns200WithExistsFlag() {
        when(documentationService.hasDocumentation(REQUEST_ID)).thenReturn(true);
        ResponseEntity<Map<String, Boolean>> res = controller.exists(REQUEST_ID);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("exists", true);

        when(documentationService.hasDocumentation(REQUEST_ID)).thenReturn(false);
        res = controller.exists(REQUEST_ID);
        assertThat(res.getBody()).containsEntry("exists", false);
    }

    @Test
    @DisplayName("listFiles — возвращает 200 и список files")
    void listFilesReturns200WithFileList() {
        when(documentationService.listFiles(REQUEST_ID))
                .thenReturn(List.of(new FileEntry("domain.puml", "puml"), new FileEntry("use_case.puml", "puml")));

        ResponseEntity<?> res = controller.listFiles(REQUEST_ID);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, List<FileEntry>> body = (Map<String, List<FileEntry>>) res.getBody();
        assertThat(body.get("files")).hasSize(2);
    }

    @Test
    @DisplayName("getFileContent — возвращает 200 и текст содержимого")
    void getFileContentReturns200WithText() {
        when(documentationService.readFileContent(REQUEST_ID, "domain.puml")).thenReturn("@startuml\nclass A\n@enduml");

        ResponseEntity<?> res = controller.getFileContent(REQUEST_ID, "domain.puml");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo("@startuml\nclass A\n@enduml");
    }
}
