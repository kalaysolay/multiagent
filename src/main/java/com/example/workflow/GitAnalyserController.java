package com.example.workflow;

import com.example.portal.agents.git.model.GitAnalysisRequest;
import com.example.portal.agents.git.model.GitAnalysisResponse;
import com.example.portal.agents.git.service.FileReferenceAnalyzer;
import com.example.portal.agents.git.service.GitRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/git-analyser")
@RequiredArgsConstructor
@Slf4j
public class GitAnalyserController {
    
    private final GitRepositoryService gitRepositoryService;
    private final FileReferenceAnalyzer fileReferenceAnalyzer;
    
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeRepository(@RequestBody GitAnalysisRequest request) {
        String requestId = UUID.randomUUID().toString();
        Path repoPath = null;
        
        try {
            log.info("Starting Git analysis for repository: {} branch: {}", 
                    request.repositoryUrl(), request.branch());
            
            // Клонируем репозиторий
            repoPath = gitRepositoryService.cloneRepository(
                    request.repositoryUrl(),
                    request.branch(),
                    request.accessToken()
            );
            
            // Получаем список всех файлов
            List<String> allFiles = gitRepositoryService.getAllFiles(repoPath);
            
            // Анализируем ссылки
            GitAnalysisResponse.AnalysisResult result = fileReferenceAnalyzer.analyzeRepository(
                    repoPath,
                    allFiles,
                    gitRepositoryService
            );
            
            GitAnalysisResponse response = new GitAnalysisResponse(
                    requestId,
                    request.repositoryUrl(),
                    request.branch(),
                    result
            );
            
            log.info("Git analysis completed successfully. Unused files: {}, Broken references: {}", 
                    result.unusedFiles().size(), result.brokenReferences().size());
            
            return ResponseEntity.ok(response);
            
        } catch (GitAPIException e) {
            log.error("Git API error during analysis", e);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Git error: " + e.getMessage()));
        } catch (IOException e) {
            log.error("IO error during analysis", e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("IO error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during analysis", e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Unexpected error: " + e.getMessage()));
        } finally {
            // Очищаем временную копию репозитория
            if (repoPath != null) {
                gitRepositoryService.cleanup(repoPath);
            }
        }
    }
    
    private record ErrorResponse(String error) {}
}

