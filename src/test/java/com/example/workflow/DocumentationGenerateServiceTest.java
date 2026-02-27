package com.example.workflow;

import com.example.portal.agents.iconix.entity.UseCaseMvc;
import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.entity.WorkflowSession;
import com.example.portal.agents.iconix.model.WorkflowResponse;
import com.example.portal.agents.iconix.service.UseCaseMvcService;
import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты сервиса генерации ICONIX-документации: создание папки и файлов,
 * отказ при существующей папке, список файлов и чтение содержимого.
 */
@ExtendWith(MockitoExtension.class)
class DocumentationGenerateServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private WorkflowSessionService workflowSessionService;

    @Mock
    private UseCaseScenarioService scenarioService;

    @Mock
    private UseCaseMvcService mvcService;

    @InjectMocks
    private DocumentationGenerateService service;

    private static final String REQUEST_ID = "test-request-123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "outputBasePath", tempDir.toString());
    }

    @Test
    @DisplayName("При пустом имени папки выбрасывается IllegalArgumentException")
    void generateRejectsBlankFolderName() {
        assertThatThrownBy(() -> service.generate(REQUEST_ID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Имя папки");
        assertThatThrownBy(() -> service.generate(REQUEST_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("При недопустимых символах в имени папки выбрасывается IllegalArgumentException")
    void generateRejectsInvalidFolderName() {
        assertThatThrownBy(() -> service.generate(REQUEST_ID, "folder/name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generate(REQUEST_ID, ".."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("При существующей папке выбрасывается FolderAlreadyExistsException")
    void generateThrowsWhenFolderExists() throws Exception {
        ReflectionTestUtils.setField(service, "outputBasePath", tempDir.toAbsolutePath().toString());
        Path basePath = Path.of(tempDir.toString()).toAbsolutePath().normalize();
        Path targetDir = basePath.resolve("existing_folder").normalize();
        java.nio.file.Files.createDirectories(targetDir);
        assertThat(java.nio.file.Files.exists(targetDir)).isTrue();

        // getSessionData и др. не вызываются — исключение до них
        assertThatThrownBy(() -> service.generate(REQUEST_ID, "existing_folder"))
                .isInstanceOf(DocumentationGenerateService.FolderAlreadyExistsException.class)
                .hasMessageContaining("уже существует");
    }

    @Test
    @DisplayName("Успешная генерация создаёт domain.puml, use_case.puml и сохраняет имя папки в сессии")
    void generateCreatesFilesAndSavesFolderName() throws Exception {
        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("plantuml", "domain content");
        artifacts.put("useCaseModel", "usecase content");
        when(workflowSessionService.getSessionData(REQUEST_ID))
                .thenReturn(new WorkflowResponse(REQUEST_ID, null, artifacts, List.of()));
        when(scenarioService.getScenariosByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(mvcService.getByRequestId(REQUEST_ID)).thenReturn(List.of());

        DocumentationGenerateService.GenerateResult result = service.generate(REQUEST_ID, "my_feature");

        assertThat(result.folderPath()).contains("my_feature");
        assertThat(result.createdFiles()).containsExactlyInAnyOrder("domain.puml", "use_case.puml");
        Path dir = tempDir.resolve("my_feature");
        assertThat(dir).exists().isDirectory();
        assertThat(dir.resolve("domain.puml")).content().isEqualTo("domain content");
        assertThat(dir.resolve("use_case.puml")).content().isEqualTo("usecase content");
        verify(workflowSessionService).setDocumentationFolderForSession(eq(REQUEST_ID), eq("my_feature"));
    }

    @Test
    @DisplayName("Имена файлов scn_ и mvc_ берутся из useCaseAlias (например openArticleByLink)")
    void generateUsesUseCaseAliasForFileName() {
        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("plantuml", "x");
        artifacts.put("useCaseModel", "y");
        when(workflowSessionService.getSessionData(REQUEST_ID))
                .thenReturn(new WorkflowResponse(REQUEST_ID, null, artifacts, List.of()));

        UseCaseScenario scenario = UseCaseScenario.builder()
                .id(UUID.randomUUID())
                .requestId(REQUEST_ID)
                .useCaseAlias("openArticleByLink")
                .useCaseName("Открыть статью по ссылке")
                .scenarioContent("= Сценарий")
                .build();
        when(scenarioService.getScenariosByRequestId(REQUEST_ID)).thenReturn(List.of(scenario));

        UseCaseMvc mvc = UseCaseMvc.builder()
                .id(UUID.randomUUID())
                .requestId(REQUEST_ID)
                .useCaseAlias("openArticleByLink")
                .useCaseName("Открыть статью по ссылке")
                .mvcPlantuml("@startuml\n@enduml")
                .build();
        when(mvcService.getByRequestId(REQUEST_ID)).thenReturn(List.of(mvc));

        DocumentationGenerateService.GenerateResult result = service.generate(REQUEST_ID, "feature_alias");
        assertThat(result.createdFiles()).contains("scn_openArticleByLink.adoc", "mvc_openArticleByLink.puml");
    }

    @Test
    @DisplayName("Генерация создаёт scn_*.adoc и mvc_*.puml из декомпозиции")
    void generateCreatesScenarioAndMvcFiles() {
        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("plantuml", "x");
        artifacts.put("useCaseModel", "y");
        when(workflowSessionService.getSessionData(REQUEST_ID))
                .thenReturn(new WorkflowResponse(REQUEST_ID, null, artifacts, List.of()));

        UseCaseScenario scenario = UseCaseScenario.builder()
                .id(UUID.randomUUID())
                .requestId(REQUEST_ID)
                .useCaseAlias("reviewGift")
                .useCaseName("Проверить подарок")
                .scenarioContent("= Сценарий\n\nШаг 1")
                .build();
        when(scenarioService.getScenariosByRequestId(REQUEST_ID)).thenReturn(List.of(scenario));

        UseCaseMvc mvc = UseCaseMvc.builder()
                .id(UUID.randomUUID())
                .requestId(REQUEST_ID)
                .useCaseAlias("reviewGift")
                .useCaseName("Проверить подарок")
                .mvcPlantuml("@startuml\nMVC diagram\n@enduml")
                .build();
        when(mvcService.getByRequestId(REQUEST_ID)).thenReturn(List.of(mvc));

        DocumentationGenerateService.GenerateResult result = service.generate(REQUEST_ID, "feature2");

        assertThat(result.createdFiles())
                .contains("domain.puml", "use_case.puml", "scn_reviewGift.adoc", "mvc_reviewGift.puml");
        Path dir = tempDir.resolve("feature2");
        assertThat(dir.resolve("scn_reviewGift.adoc")).content().contains("Сценарий");
        assertThat(dir.resolve("mvc_reviewGift.puml")).content().contains("MVC diagram");
    }

    @Test
    @DisplayName("В mvc_*.puml записывается только блок @startuml..@enduml, без asciidoc-обёртки")
    void mvcFileContainsOnlyPlantUmlBlock() throws Exception {
        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("plantuml", "x");
        artifacts.put("useCaseModel", "y");
        when(workflowSessionService.getSessionData(REQUEST_ID))
                .thenReturn(new WorkflowResponse(REQUEST_ID, null, artifacts, List.of()));
        when(scenarioService.getScenariosByRequestId(REQUEST_ID)).thenReturn(List.of());

        String wrapped = "== AsciiDoc\n\n[plantuml]\n----\n@startuml\nclass A { }\n@enduml\n----\n";
        UseCaseMvc mvc = UseCaseMvc.builder()
                .id(UUID.randomUUID())
                .requestId(REQUEST_ID)
                .useCaseAlias("onlyBlock")
                .useCaseName("Only block")
                .mvcPlantuml(wrapped)
                .build();
        when(mvcService.getByRequestId(REQUEST_ID)).thenReturn(List.of(mvc));

        service.generate(REQUEST_ID, "mvc_only_block");
        String written = java.nio.file.Files.readString(tempDir.resolve("mvc_only_block").resolve("mvc_onlyBlock.puml"));
        assertThat(written).startsWith("@startuml").contains("class A").endsWith("@enduml");
        assertThat(written).doesNotContain("AsciiDoc").doesNotContain("[plantuml]").doesNotContain("----");
    }

    @Test
    @DisplayName("При повторной генерации в ту же папку (обновление) файлы перезаписываются, 409 не выбрасывается")
    void generateUpdateModeWhenFolderBelongsToSession() throws Exception {
        ReflectionTestUtils.setField(service, "outputBasePath", tempDir.toAbsolutePath().toString());
        Path targetDir = tempDir.resolve("update_folder");
        java.nio.file.Files.createDirectories(targetDir);
        java.nio.file.Files.writeString(targetDir.resolve("domain.puml"), "old");

        WorkflowSession session = new WorkflowSession();
        session.setRequestId(REQUEST_ID);
        session.setDocumentationFolderName("update_folder");
        when(workflowSessionService.loadSession(REQUEST_ID)).thenReturn(Optional.of(session));

        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("plantuml", "new domain");
        artifacts.put("useCaseModel", "new usecase");
        when(workflowSessionService.getSessionData(REQUEST_ID))
                .thenReturn(new WorkflowResponse(REQUEST_ID, null, artifacts, List.of()));
        when(scenarioService.getScenariosByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(mvcService.getByRequestId(REQUEST_ID)).thenReturn(List.of());

        DocumentationGenerateService.GenerateResult result = service.generate(REQUEST_ID, "update_folder");
        assertThat(result.updated()).isTrue();
        assertThat(targetDir.resolve("domain.puml")).content().isEqualTo("new domain");
    }

    @Test
    @DisplayName("hasDocumentation возвращает true только если папка сохранена и существует на диске")
    void hasDocumentationReturnsTrueOnlyWhenFolderExists() throws Exception {
        WorkflowSession session = new WorkflowSession();
        session.setRequestId(REQUEST_ID);
        session.setDocumentationFolderName("existing_docs");
        when(workflowSessionService.loadSession(REQUEST_ID)).thenReturn(Optional.of(session));

        assertThat(service.hasDocumentation(REQUEST_ID)).isFalse();

        java.nio.file.Files.createDirectories(tempDir.resolve("existing_docs"));
        assertThat(service.hasDocumentation(REQUEST_ID)).isTrue();
    }

    @Test
    @DisplayName("listFiles возвращает отсортированный список файлов")
    void listFilesReturnsSortedFileList() throws Exception {
        Path dir = tempDir.resolve("list_test");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("domain.puml"), "a");
        java.nio.file.Files.writeString(dir.resolve("use_case.puml"), "b");

        WorkflowSession session = new WorkflowSession();
        session.setRequestId(REQUEST_ID);
        session.setDocumentationFolderName("list_test");
        when(workflowSessionService.loadSession(REQUEST_ID)).thenReturn(Optional.of(session));

        List<DocumentationGenerateService.FileEntry> files = service.listFiles(REQUEST_ID);
        assertThat(files).hasSize(2);
        assertThat(files.get(0).name()).isEqualTo("domain.puml");
        assertThat(files.get(1).name()).isEqualTo("use_case.puml");
    }

    @Test
    @DisplayName("readFileContent возвращает содержимое файла и отклоняет path traversal")
    void readFileContentReturnsContentAndRejectsPathTraversal() throws Exception {
        Path dir = tempDir.resolve("read_test");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("domain.puml"), "plantuml content");

        WorkflowSession session = new WorkflowSession();
        session.setRequestId(REQUEST_ID);
        session.setDocumentationFolderName("read_test");
        when(workflowSessionService.loadSession(REQUEST_ID)).thenReturn(Optional.of(session));

        assertThat(service.readFileContent(REQUEST_ID, "domain.puml")).isEqualTo("plantuml content");
        assertThatThrownBy(() -> service.readFileContent(REQUEST_ID, "../read_test/domain.puml"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
