package com.example.portal.agents.iconix.model;

/**
 * Тело запроса на возобновление workflow после паузы на ревью.
 * Передаётся отредактированный нарратив и/или доменная модель.
 */
public record ResumeRequest(
        String requestId,
        String narrative,
        String domainModel
) {}
