package com.example.portal.agents.git.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для работы с Git репозиториями.
 * Клонирует репозиторий, переключается на указанную ветку и предоставляет доступ к файлам.
 */
@Slf4j
@Service
public class GitRepositoryService {
    
    private static final String TEMP_DIR_PREFIX = "git-repo-";
    private final Path tempBaseDir;
    
    public GitRepositoryService() {
        try {
            this.tempBaseDir = Files.createTempDirectory("git-analyser");
            log.info("Git repository service initialized. Temp directory: {}", tempBaseDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for Git repositories", e);
        }
    }
    
    /**
     * Клонирует репозиторий и переключается на указанную ветку.
     * 
     * @param repositoryUrl URL репозитория
     * @param branch ветка для анализа
     * @param accessToken токен доступа (опционально)
     * @return путь к локальной копии репозитория
     */
    public Path cloneRepository(String repositoryUrl, String branch, String accessToken) throws GitAPIException, IOException {
        String sessionId = UUID.randomUUID().toString();
        Path repoPath = tempBaseDir.resolve(TEMP_DIR_PREFIX + sessionId);
        
        log.info("Cloning repository: {} branch: {}", repositoryUrl, branch);
        
        // Подготавливаем URL с токеном, если он указан
        String cloneUrl = repositoryUrl;
        if (accessToken != null && !accessToken.isBlank()) {
            // Добавляем токен в URL для приватных репозиториев
            if (repositoryUrl.startsWith("https://")) {
                cloneUrl = repositoryUrl.replace("https://", 
                    "https://" + accessToken + "@");
            } else if (repositoryUrl.startsWith("http://")) {
                cloneUrl = repositoryUrl.replace("http://", 
                    "http://" + accessToken + "@");
            }
        }
        
        // Клонируем репозиторий
        Git git = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(repoPath.toFile())
                .setBranch(branch)
                .setCloneSubmodules(false)
                .call();
        
        log.info("Repository cloned successfully to: {}", repoPath);
        
        // Убеждаемся, что мы на правильной ветке
        git.checkout()
                .setName(branch)
                .call();
        
        git.close();
        
        return repoPath;
    }
    
    /**
     * Получает список всех файлов в репозитории.
     * 
     * @param repoPath путь к локальной копии репозитория
     * @return список путей к файлам (относительно корня репозитория)
     */
    public List<String> getAllFiles(Path repoPath) throws IOException, GitAPIException {
        List<String> files = new ArrayList<>();
        
        try (Git git = Git.open(repoPath.toFile());
             Repository repository = git.getRepository();
             RevWalk revWalk = new RevWalk(repository)) {
            
            // Получаем HEAD коммит
            RevCommit commit = revWalk.parseCommit(repository.resolve("HEAD"));
            RevTree tree = commit.getTree();
            
            // Обходим дерево файлов
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                
                while (treeWalk.next()) {
                    if (!treeWalk.isSubtree()) {
                        String filePath = treeWalk.getPathString();
                        // Пропускаем .git директорию и другие служебные файлы
                        if (!filePath.startsWith(".git/") && 
                            !filePath.equals(".gitignore") &&
                            !filePath.equals(".gitattributes")) {
                            files.add(filePath);
                        }
                    }
                }
            }
        }
        
        log.info("Found {} files in repository", files.size());
        return files;
    }
    
    /**
     * Читает содержимое файла из репозитория.
     */
    public String readFile(Path repoPath, String filePath) throws IOException {
        Path fullPath = repoPath.resolve(filePath);
        if (!Files.exists(fullPath)) {
            return null;
        }
        return Files.readString(fullPath);
    }
    
    /**
     * Удаляет временную копию репозитория.
     */
    public void cleanup(Path repoPath) {
        try {
            if (Files.exists(repoPath)) {
                deleteDirectory(repoPath.toFile());
                log.info("Cleaned up repository: {}", repoPath);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup repository: {}", repoPath, e);
        }
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}

