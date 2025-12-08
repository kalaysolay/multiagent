package com.example.portal.agents.git.service;

import com.example.portal.agents.git.model.GitAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Анализатор ссылок на файлы в коде.
 * Находит импорты, require, include и другие ссылки на файлы.
 */
@Slf4j
@Service
public class FileReferenceAnalyzer {
    
    // Паттерны для различных типов ссылок на файлы
    private static final List<ReferencePattern> REFERENCE_PATTERNS = List.of(
        // Java imports
        new ReferencePattern("import", Pattern.compile("^\\s*import\\s+([\\w.]+)\\s*;", Pattern.MULTILINE), "java"),
        // JavaScript/TypeScript imports
        new ReferencePattern("import", Pattern.compile("import\\s+.*?from\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE), "js", "ts", "jsx", "tsx"),
        // JavaScript require
        new ReferencePattern("require", Pattern.compile("require\\(['\"]([^'\"]+)['\"]\\)", Pattern.MULTILINE), "js", "ts"),
        // PHP include/require
        new ReferencePattern("include", Pattern.compile("(?:include|require)(?:_once)?\\s*['\"]([^'\"]+)['\"]", Pattern.MULTILINE), "php"),
        // Python imports
        new ReferencePattern("import", Pattern.compile("^\\s*(?:from\\s+)?import\\s+([\\w.]+)", Pattern.MULTILINE), "py"),
        // C/C++ includes
        new ReferencePattern("include", Pattern.compile("#include\\s+[<\"]([^>\"]+)[>\"]", Pattern.MULTILINE), "c", "cpp", "h", "hpp"),
        // HTML/CSS links
        new ReferencePattern("link", Pattern.compile("(?:href|src)=['\"]([^'\"]+)['\"]", Pattern.MULTILINE), "html", "htm", "css"),
        // XML/XSL includes
        new ReferencePattern("include", Pattern.compile("(?:href|src)=['\"]([^'\"]+)['\"]", Pattern.MULTILINE), "xml", "xsl"),
        // JSON references (в некоторых конфигах)
        new ReferencePattern("path", Pattern.compile("['\"]([^'\"]+\\.(?:json|yaml|yml|properties|conf|config))['\"]", Pattern.MULTILINE), "json", "yaml", "yml")
    );
    
    /**
     * Анализирует репозиторий и находит неиспользуемые файлы и битые ссылки.
     */
    public GitAnalysisResponse.AnalysisResult analyzeRepository(
            Path repoPath, 
            List<String> allFiles,
            GitRepositoryService gitService) throws IOException {
        
        log.info("Starting file reference analysis for {} files", allFiles.size());
        
        // Строим карту всех файлов (нормализованные пути)
        Map<String, String> fileMap = buildFileMap(allFiles);
        
        // Находим все ссылки на файлы
        Map<String, Set<String>> fileReferences = new HashMap<>();
        List<GitAnalysisResponse.BrokenReference> brokenReferences = new ArrayList<>();
        
        int analyzedFiles = 0;
        
        for (String filePath : allFiles) {
            if (shouldAnalyzeFile(filePath)) {
                analyzedFiles++;
                String content = gitService.readFile(repoPath, filePath);
                if (content != null) {
                    Set<String> references = extractReferences(filePath, content);
                    fileReferences.put(filePath, references);
                    
                    // Проверяем, существуют ли ссылаемые файлы
                    for (String ref : references) {
                        if (!fileExists(ref, fileMap)) {
                            brokenReferences.add(new GitAnalysisResponse.BrokenReference(
                                    filePath,
                                    ref,
                                    findLineNumber(content, ref),
                                    detectReferenceType(filePath, ref)
                            ));
                        }
                    }
                }
            }
        }
        
        // Находим неиспользуемые файлы
        List<GitAnalysisResponse.UnusedFile> unusedFiles = findUnusedFiles(
                allFiles, 
                fileReferences, 
                fileMap,
                repoPath
        );
        
        log.info("Analysis complete: {} unused files, {} broken references", 
                unusedFiles.size(), brokenReferences.size());
        
        return new GitAnalysisResponse.AnalysisResult(
                unusedFiles,
                brokenReferences,
                allFiles.size(),
                analyzedFiles
        );
    }
    
    /**
     * Строит карту файлов с нормализованными путями.
     */
    private Map<String, String> buildFileMap(List<String> allFiles) {
        Map<String, String> fileMap = new HashMap<>();
        for (String file : allFiles) {
            // Добавляем файл как есть
            fileMap.put(normalizePath(file), file);
            // Добавляем без расширения
            String withoutExt = removeExtension(file);
            if (!withoutExt.equals(file)) {
                fileMap.put(normalizePath(withoutExt), file);
            }
            // Добавляем только имя файла
            String fileName = getFileName(file);
            fileMap.put(normalizePath(fileName), file);
        }
        return fileMap;
    }
    
    /**
     * Извлекает все ссылки на файлы из содержимого файла.
     */
    private Set<String> extractReferences(String filePath, String content) {
        Set<String> references = new HashSet<>();
        String extension = getFileExtension(filePath);
        
        for (ReferencePattern pattern : REFERENCE_PATTERNS) {
            if (pattern.matchesExtension(extension)) {
                Matcher matcher = pattern.pattern.matcher(content);
                while (matcher.find()) {
                    String ref = matcher.group(1);
                    // Нормализуем ссылку относительно текущего файла
                    String normalizedRef = resolveReference(filePath, ref);
                    if (normalizedRef != null) {
                        references.add(normalizedRef);
                    }
                }
            }
        }
        
        return references;
    }
    
    /**
     * Разрешает относительную ссылку в абсолютный путь относительно корня репозитория.
     */
    private String resolveReference(String sourceFile, String reference) {
        // Убираем параметры запроса и якоря из URL
        reference = reference.split("[?#]")[0];
        
        // Если ссылка начинается с /, это абсолютный путь
        if (reference.startsWith("/")) {
            return reference.substring(1);
        }
        
        // Если ссылка начинается с ./, ../ или просто имя файла
        String sourceDir = getDirectory(sourceFile);
        Path sourcePath = Path.of(sourceDir);
        Path refPath = sourcePath.resolve(reference).normalize();
        
        // Преобразуем в строку относительно корня
        String result = refPath.toString().replace("\\", "/");
        
        // Убираем ведущие точки
        while (result.startsWith("../")) {
            result = result.substring(3);
        }
        
        return result;
    }
    
    /**
     * Проверяет, существует ли файл по указанному пути.
     */
    private boolean fileExists(String reference, Map<String, String> fileMap) {
        // Пробуем разные варианты нормализации
        String normalized = normalizePath(reference);
        if (fileMap.containsKey(normalized)) {
            return true;
        }
        
        // Пробуем без расширения
        String withoutExt = removeExtension(normalized);
        if (fileMap.containsKey(withoutExt)) {
            return true;
        }
        
        // Пробуем только имя файла
        String fileName = getFileName(reference);
        if (fileMap.containsKey(normalizePath(fileName))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Находит неиспользуемые файлы.
     */
    private List<GitAnalysisResponse.UnusedFile> findUnusedFiles(
            List<String> allFiles,
            Map<String, Set<String>> fileReferences,
            Map<String, String> fileMap,
            Path repoPath) throws IOException {
        
        List<GitAnalysisResponse.UnusedFile> unusedFiles = new ArrayList<>();
        
        // Собираем все файлы, на которые есть ссылки
        Set<String> referencedFiles = new HashSet<>();
        for (Set<String> refs : fileReferences.values()) {
            for (String ref : refs) {
                String actualFile = fileMap.get(normalizePath(ref));
                if (actualFile != null) {
                    referencedFiles.add(actualFile);
                }
            }
        }
        
        // Находим файлы, на которые нет ссылок
        for (String file : allFiles) {
            if (shouldCheckForUnused(file) && !referencedFiles.contains(file)) {
                long fileSize = getFileSize(repoPath, file);
                unusedFiles.add(new GitAnalysisResponse.UnusedFile(
                        file,
                        "No references found in codebase",
                        fileSize
                ));
            }
        }
        
        return unusedFiles;
    }
    
    private boolean shouldAnalyzeFile(String filePath) {
        String ext = getFileExtension(filePath).toLowerCase();
        // Анализируем только текстовые файлы с кодом
        return !ext.isEmpty() && !ext.equals("md") && !ext.equals("txt") && 
               !ext.equals("log") && !ext.equals("gitignore");
    }
    
    private boolean shouldCheckForUnused(String filePath) {
        String ext = getFileExtension(filePath).toLowerCase();
        // Не проверяем конфигурационные файлы, документацию и т.д.
        return !ext.equals("md") && !ext.equals("txt") && 
               !ext.equals("readme") && !filePath.contains("README") &&
               !filePath.contains(".git") && !filePath.contains("node_modules");
    }
    
    private String normalizePath(String path) {
        return path.replace("\\", "/").toLowerCase();
    }
    
    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot + 1) : "";
    }
    
    private String removeExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(0, lastDot) : filePath;
    }
    
    private String getFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
    
    private String getDirectory(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(0, lastSlash) : "";
    }
    
    private int findLineNumber(String content, String searchText) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(searchText)) {
                return i + 1;
            }
        }
        return 0;
    }
    
    private String detectReferenceType(String filePath, String reference) {
        String ext = getFileExtension(filePath).toLowerCase();
        if (ext.equals("java")) return "import";
        if (ext.equals("js") || ext.equals("ts")) return reference.contains("require") ? "require" : "import";
        if (ext.equals("php")) return "include";
        if (ext.equals("py")) return "import";
        if (ext.equals("c") || ext.equals("cpp") || ext.equals("h")) return "include";
        if (ext.equals("html") || ext.equals("css")) return "link";
        return "path";
    }
    
    private long getFileSize(Path repoPath, String filePath) {
        try {
            Path fullPath = repoPath.resolve(filePath);
            return Files.exists(fullPath) ? Files.size(fullPath) : 0;
        } catch (IOException e) {
            return 0;
        }
    }
    
    private record ReferencePattern(
            String type,
            Pattern pattern,
            String... extensions
    ) {
        boolean matchesExtension(String ext) {
            for (String e : extensions) {
                if (e.equalsIgnoreCase(ext)) {
                    return true;
                }
            }
            return false;
        }
    }
}

