package com.example.portal.agents.git.model;

import java.util.List;

public record GitAnalysisResponse(
        String requestId,
        String repositoryUrl,
        String branch,
        AnalysisResult result
) {
    public record AnalysisResult(
            List<UnusedFile> unusedFiles,
            List<BrokenReference> brokenReferences,
            int totalFiles,
            int analyzedFiles
    ) {}
    
    public record UnusedFile(
            String filePath,
            String reason,
            long fileSize
    ) {}
    
    public record BrokenReference(
            String sourceFile,
            String referencedPath,
            int lineNumber,
            String referenceType  // import, require, include, path, etc.
    ) {}
}

