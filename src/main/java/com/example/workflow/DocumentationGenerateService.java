package com.example.workflow;

import com.example.portal.agents.iconix.entity.UseCaseMvc;
import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.model.WorkflowResponse;
import com.example.portal.agents.iconix.service.UseCaseMvcService;
import com.example.portal.agents.iconix.service.UseCaseScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Генерирует ICONIX-документацию в папку на сервере: domain.puml, use_case.puml,
 * scn_&lt;alias&gt;.adoc и mvc_&lt;alias&gt;.puml. После успешной генерации сохраняет
 * имя папки в сессии workflow для кнопки «Документация».
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentationGenerateService {

    private static final String FOLDER_NAME_PATTERN = "[a-zA-Z0-9_-]+";
    private static final String SAFE_ALIAS_PATTERN = "[^a-zA-Z0-9_-]";

    @Value("${app.documentation.output-base-path:./generated-docs}")
    private String outputBasePath;

    private final WorkflowSessionService workflowSessionService;
    private final UseCaseScenarioService scenarioService;
    private final UseCaseMvcService mvcService;

    /**
     * Создаёт папку с именем folderName и записывает в неё файлы документации.
     *
     * @param requestId идентификатор workflow-сессии
     * @param folderName имя папки (только буквы, цифры, дефис, подчёркивание)
     * @return результат с путём и списком созданных файлов
     * @throws IllegalArgumentException если сессия не найдена или folderName невалидно
     * @throws FolderAlreadyExistsException если папка уже существует
     */
    public GenerateResult generate(String requestId, String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Имя папки не задано");
        }
        if (!folderName.matches(FOLDER_NAME_PATTERN)) {
            throw new IllegalArgumentException("Имя папки может содержать только буквы, цифры, дефис и подчёркивание");
        }
        // Запрет path traversal
        if (folderName.contains("..") || folderName.contains("/") || folderName.contains("\\")) {
            throw new IllegalArgumentException("Недопустимое имя папки");
        }

        Path basePath = Path.of(outputBasePath).toAbsolutePath().normalize();
        Path targetDir = basePath.resolve(folderName).normalize();
        if (!targetDir.startsWith(basePath)) {
            throw new IllegalArgumentException("Недопустимое имя папки");
        }

        // Если папка уже существует: разрешаем обновление только для этой же сессии (догенерация по новым сценариям/MVC)
        boolean updateMode = false;
        if (Files.exists(targetDir)) {
            String existingFolder = workflowSessionService.loadSession(requestId)
                    .map(s -> s.getDocumentationFolderName())
                    .filter(f -> f != null && !f.isBlank())
                    .orElse(null);
            if (folderName.equals(existingFolder)) {
                updateMode = true;
            } else {
                throw new FolderAlreadyExistsException("Каталог с таким именем уже существует: " + folderName);
            }
        }

        WorkflowResponse sessionData = workflowSessionService.getSessionData(requestId);
        Map<String, Object> artifacts = sessionData.artifacts();
        if (artifacts == null) {
            throw new IllegalStateException("Нет артефактов в сессии: " + requestId);
        }

        List<String> createdFiles = new ArrayList<>();

        if (!updateMode) {
            try {
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                log.error("Failed to create directory: {}", targetDir, e);
                throw new RuntimeException("Не удалось создать каталог: " + e.getMessage());
            }
        }

        try {
            // domain.puml
            Object plantuml = artifacts.get("plantuml");
            if (plantuml != null && !String.valueOf(plantuml).isBlank()) {
                writeFile(targetDir, "domain.puml", String.valueOf(plantuml));
                createdFiles.add("domain.puml");
            }

            // use_case.puml
            Object useCaseModel = artifacts.get("useCaseModel");
            if (useCaseModel != null && !String.valueOf(useCaseModel).isBlank()) {
                writeFile(targetDir, "use_case.puml", String.valueOf(useCaseModel));
                createdFiles.add("use_case.puml");
            }

            // scn_<alias>.adoc — имя из useCaseAlias (например openArticleByLink), иначе из useCaseName, иначе id
            List<UseCaseScenario> scenarios = scenarioService.getScenariosByRequestId(requestId);
            for (UseCaseScenario s : scenarios) {
                String alias = fileNamePartFromScenario(s);
                String fileName = "scn_" + alias + ".adoc";
                writeFile(targetDir, fileName, s.getScenarioContent() != null ? s.getScenarioContent() : "");
                createdFiles.add(fileName);
            }

            // mvc_<alias>.puml — только блок @startuml..@enduml, иначе не отрендерится и может содержать asciidoc
            List<UseCaseMvc> mvcList = mvcService.getByRequestId(requestId);
            for (UseCaseMvc m : mvcList) {
                String alias = fileNamePartFromMvc(m);
                String fileName = "mvc_" + alias + ".puml";
                String mvcContent = extractPlantUmlBlock(m.getMvcPlantuml());
                writeFile(targetDir, fileName, mvcContent);
                createdFiles.add(fileName);
            }

            workflowSessionService.setDocumentationFolderForSession(requestId, folderName);
            log.info("Documentation {} for requestId={}, folder={}, files={}", updateMode ? "updated" : "generated", requestId, folderName, createdFiles);

            return new GenerateResult(targetDir.toString(), createdFiles, updateMode);
        } catch (IOException e) {
            log.error("Failed to write documentation files: {}", targetDir, e);
            throw new RuntimeException("Ошибка записи файлов: " + e.getMessage());
        }
    }

    private void writeFile(Path dir, String fileName, String content) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** Оставляет в алиасе только безопасные для имени файла символы. */
    private static String sanitizeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return "";
        }
        return alias.replaceAll(SAFE_ALIAS_PATTERN, "_").trim();
    }

    /** Имя файла для сценария: useCaseAlias (например openArticleByLink), иначе санитизированное useCaseName, иначе scenario_id. */
    private static String fileNamePartFromScenario(UseCaseScenario s) {
        String alias = sanitizeAlias(s.getUseCaseAlias());
        if (!alias.isEmpty()) {
            return alias;
        }
        String fromName = sanitizeAlias(s.getUseCaseName());
        if (!fromName.isEmpty()) {
            return fromName;
        }
        return "scenario_" + s.getId();
    }

    /** Имя файла для MVC: useCaseAlias, иначе useCaseName, иначе mvc_id. */
    private static String fileNamePartFromMvc(UseCaseMvc m) {
        String alias = sanitizeAlias(m.getUseCaseAlias());
        if (!alias.isEmpty()) {
            return alias;
        }
        String fromName = sanitizeAlias(m.getUseCaseName());
        if (!fromName.isEmpty()) {
            return fromName;
        }
        return "mvc_" + m.getId();
    }

    /**
     * Извлекает из текста только блок PlantUML (от @startuml до @enduml включительно).
     * Иначе в файле может оказаться asciidoc или обёртка — диаграмма не отрендерится.
     */
    private static String extractPlantUmlBlock(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase();
        int start = lower.indexOf("@startuml");
        if (start < 0) {
            return "";
        }
        int end = lower.indexOf("@enduml", start);
        if (end < 0) {
            return text.substring(start).trim();
        }
        return text.substring(start, end + "@enduml".length()).trim();
    }

    /**
     * Проверяет, есть ли для сессии сгенерированная документация (папка сохранена и существует на диске).
     */
    public boolean hasDocumentation(String requestId) {
        return workflowSessionService.loadSession(requestId)
                .map(s -> {
                    String folder = s.getDocumentationFolderName();
                    if (folder == null || folder.isBlank()) {
                        return false;
                    }
                    Path path = Path.of(outputBasePath).toAbsolutePath().normalize().resolve(folder).normalize();
                    return path.startsWith(Path.of(outputBasePath).toAbsolutePath().normalize()) && Files.isDirectory(path);
                })
                .orElse(false);
    }

    /**
     * Возвращает список имён файлов в папке документации сессии.
     */
    public List<FileEntry> listFiles(String requestId) {
        Path dir = resolveDocumentationDir(requestId);
        List<FileEntry> result = new ArrayList<>();
        try {
            Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .map(name -> new FileEntry(name, fileType(name)))
                    .forEach(result::add);
        } catch (IOException e) {
            log.warn("Cannot list files in {}: {}", dir, e.getMessage());
        }
        result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
        return result;
    }

    /**
     * Читает содержимое файла из папки документации сессии. Имя файла должно быть без пути (защита от path traversal).
     */
    public String readFileContent(String requestId, String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Недопустимое имя файла");
        }
        Path dir = resolveDocumentationDir(requestId);
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("Недопустимое имя файла");
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Cannot read file {}: {}", file, e.getMessage());
            throw new RuntimeException("Файл не найден или недоступен: " + fileName);
        }
    }

    private Path resolveDocumentationDir(String requestId) {
        String folderName = workflowSessionService.loadSession(requestId)
                .map(s -> s.getDocumentationFolderName())
                .filter(f -> f != null && !f.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена или документация не генерировалась: " + requestId));
        Path base = Path.of(outputBasePath).toAbsolutePath().normalize();
        Path dir = base.resolve(folderName).normalize();
        if (!dir.startsWith(base) || !Files.isDirectory(dir)) {
            throw new IllegalStateException("Папка документации не найдена: " + folderName);
        }
        return dir;
    }

    private static String fileType(String name) {
        if (name.endsWith(".puml")) {
            return "puml";
        }
        if (name.endsWith(".adoc")) {
            return "adoc";
        }
        return "text";
    }

    public record GenerateResult(String folderPath, List<String> createdFiles, boolean updated) {
        public GenerateResult(String folderPath, List<String> createdFiles) {
            this(folderPath, createdFiles, false);
        }
    }

    public record FileEntry(String name, String type) {}

    /** Выбрасывается, если папка с указанным именем уже существует. */
    public static class FolderAlreadyExistsException extends RuntimeException {
        public FolderAlreadyExistsException(String message) {
            super(message);
        }
    }
}
