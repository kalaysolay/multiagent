package com.example.portal.agents.iconix.model;

/** Тело запроса на запуск workflow. Ввод пользователя — только цель (goal). domainModel — результат агента, в run не передаётся. */
public record WorkflowRequest(
        String requestId,
        String goal
) {}

