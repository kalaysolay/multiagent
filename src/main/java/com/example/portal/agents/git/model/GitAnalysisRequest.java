package com.example.portal.agents.git.model;

public record GitAnalysisRequest(
        String repositoryUrl,
        String branch,
        String accessToken  // Опционально, для приватных репозиториев
) {}

