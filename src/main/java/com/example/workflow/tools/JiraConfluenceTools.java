package com.example.workflow.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Инструменты для доступа к Jira и Confluence.
 * Оформляют ручки вашего стартера как tools для агента: агент может сам вызывать
 * getJiraIssue → getJiraRelatedIssues → getConfluencePage и т.д. по цепочке.
 *
 * Подключение вашего стартера: реализуйте интерфейс {@link JiraConfluenceApi}
 * и зарегистрируйте бин — методы ниже будут делегировать в него.
 * Если бин не зарегистрирован, инструменты возвращают сообщение «не настроено».
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraConfluenceTools {

    @Autowired(required = false)
    private JiraConfluenceApi jiraConfluenceApi;

    /**
     * Получить задачу Jira по ключу (например TASK-12345).
     * Возвращает сводку: ключ, название, описание, статус, тип, ссылки в описании и т.д.
     */
    public String getJiraIssue(String issueKey) {
        if (issueKey == null || issueKey.isBlank()) {
            return "Ошибка: укажите ключ задачи (issueKey), например TASK-12345";
        }
        if (jiraConfluenceApi == null) {
            return "Интеграция с Jira не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: getJiraIssue({})", issueKey);
            return jiraConfluenceApi.getIssue(issueKey.trim());
        } catch (Exception e) {
            log.error("Error in getJiraIssue({})", issueKey, e);
            return "Ошибка при получении задачи " + issueKey + ": " + e.getMessage();
        }
    }

    /**
     * Получить связанные задачи (issue links): блокирует, дубликаты, связанные и т.д.
     * Возвращает список ключей и типов связей, по которым можно дальше вызвать getJiraIssue.
     */
    public String getJiraRelatedIssues(String issueKey) {
        if (issueKey == null || issueKey.isBlank()) {
            return "Ошибка: укажите ключ задачи (issueKey)";
        }
        if (jiraConfluenceApi == null) {
            return "Интеграция с Jira не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: getJiraRelatedIssues({})", issueKey);
            return jiraConfluenceApi.getRelatedIssues(issueKey.trim());
        } catch (Exception e) {
            log.error("Error in getJiraRelatedIssues({})", issueKey, e);
            return "Ошибка при получении связанных задач: " + e.getMessage();
        }
    }

    /**
     * Получить удалённые ссылки задачи (remote links), в т.ч. ссылки на страницы Confluence.
     * По идентификаторам/ключам из результата можно вызвать getConfluencePage.
     */
    public String getJiraRemoteLinks(String issueKey) {
        if (issueKey == null || issueKey.isBlank()) {
            return "Ошибка: укажите ключ задачи (issueKey)";
        }
        if (jiraConfluenceApi == null) {
            return "Интеграция с Jira не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: getJiraRemoteLinks({})", issueKey);
            return jiraConfluenceApi.getRemoteLinks(issueKey.trim());
        } catch (Exception e) {
            log.error("Error in getJiraRemoteLinks({})", issueKey, e);
            return "Ошибка при получении удалённых ссылок: " + e.getMessage();
        }
    }

    /**
     * Получить контент страницы Confluence по ID или по ключу (spaceKey + pageKey).
     * pageIdOrKey — либо числовой id, либо ключ вида "SPACEKEY-123" или идентификатор страницы.
     */
    public String getConfluencePage(String pageIdOrKey) {
        if (pageIdOrKey == null || pageIdOrKey.isBlank()) {
            return "Ошибка: укажите pageIdOrKey (id страницы или ключ)";
        }
        if (jiraConfluenceApi == null) {
            return "Интеграция с Confluence не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: getConfluencePage({})", pageIdOrKey);
            return jiraConfluenceApi.getConfluencePage(pageIdOrKey.trim());
        } catch (Exception e) {
            log.error("Error in getConfluencePage({})", pageIdOrKey, e);
            return "Ошибка при получении страницы Confluence: " + e.getMessage();
        }
    }

    /**
     * Поиск страниц Confluence по CQL или текстовому запросу.
     * Результат можно использовать для вызова getConfluencePage по id/ключу.
     */
    public String searchConfluence(String query) {
        if (query == null || query.isBlank()) {
            return "Ошибка: укажите поисковый запрос (query)";
        }
        if (jiraConfluenceApi == null) {
            return "Интеграция с Confluence не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: searchConfluence({})", query);
            return jiraConfluenceApi.searchConfluence(query.trim());
        } catch (Exception e) {
            log.error("Error in searchConfluence", e);
            return "Ошибка при поиске в Confluence: " + e.getMessage();
        }
    }

    /**
     * Поиск задач по JQL (например assignee = currentUser(), или project = X AND Sprint in openSprints()).
     */
    public String searchByJql(String jql, int limit) {
        if (jql == null || jql.isBlank()) {
            return "Ошибка: укажите JQL-запрос (jql)";
        }
        if (limit <= 0) {
            limit = 50;
        }
        if (jiraConfluenceApi == null) {
            return "Интеграция с Jira не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: searchByJql(jql={}, limit={})", jql, limit);
            return jiraConfluenceApi.searchByJql(jql.trim(), limit);
        } catch (Exception e) {
            log.error("Error in searchByJql", e);
            return "Ошибка при поиске по JQL: " + e.getMessage();
        }
    }

    /**
     * Задачи текущего спринта по проекту из конфига (app.jira.project-code).
     */
    public String getJiraTasksInCurrentSprint() {
        if (jiraConfluenceApi == null) {
            return "Интеграция с Jira не настроена. Подключите JiraConfluenceApi bean.";
        }
        try {
            log.info("Tool call: getJiraTasksInCurrentSprint()");
            return jiraConfluenceApi.getJiraTasksInCurrentSprint();
        } catch (Exception e) {
            log.error("Error in getJiraTasksInCurrentSprint", e);
            return "Ошибка при получении задач спринта: " + e.getMessage();
        }
    }

    /**
     * API вашего стартера Jira/Confluence. Реализуйте этот интерфейс и зарегистрируйте бин.
     */
    public interface JiraConfluenceApi {
        /** Получить задачу по ключу (TASK-12345). Возврат: текст/JSON с полями задачи. */
        String getIssue(String issueKey);

        /** Связанные задачи (issue links). Возврат: список ключей и типов связей. */
        String getRelatedIssues(String issueKey);

        /** Удалённые ссылки (в т.ч. на Confluence). Возврат: url, id страниц и т.д. */
        String getRemoteLinks(String issueKey);

        /** Контент страницы Confluence по id или ключу. */
        String getConfluencePage(String pageIdOrKey);

        /** Поиск в Confluence (CQL или текст). */
        String searchConfluence(String query);

        /** Поиск задач по JQL. Возврат: JSON или текст с issues, total и т.д. */
        String searchByJql(String jql, int limit);

        /** Задачи текущего спринта по проекту из конфига (project-code). */
        String getJiraTasksInCurrentSprint();
    }
}
