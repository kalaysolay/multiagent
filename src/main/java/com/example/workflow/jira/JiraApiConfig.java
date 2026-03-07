package com.example.workflow.jira;

import com.example.workflow.confluence.ConfluenceApiClient;
import com.example.workflow.tools.JiraConfluenceTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Реализация JiraConfluenceApi через JiraApiClient. Подключается только при заданном app.jira.base-path.
 * Confluence опционально — при наличии ConfluenceApiClient getConfluencePage возвращает данные.
 */
@Configuration
@ConditionalOnProperty(name = "app.jira.base-path")
@Slf4j
public class JiraApiConfig {

    @Bean
    public JiraConfluenceTools.JiraConfluenceApi jiraConfluenceApi(
            JiraApiClient jiraApiClient,
            @Value("${app.jira.project-code:}") String projectCode,
            @Autowired(required = false) ConfluenceApiClient confluenceApiClient) {
        return new JiraConfluenceApiImpl(jiraApiClient, projectCode != null ? projectCode.trim() : "", confluenceApiClient);
    }

    @Slf4j
    private static class JiraConfluenceApiImpl implements JiraConfluenceTools.JiraConfluenceApi {
        private final JiraApiClient jiraApiClient;
        private final String projectCode;
        private final ConfluenceApiClient confluenceApiClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

        JiraConfluenceApiImpl(JiraApiClient jiraApiClient, String projectCode, ConfluenceApiClient confluenceApiClient) {
            this.jiraApiClient = jiraApiClient;
            this.projectCode = projectCode;
            this.confluenceApiClient = confluenceApiClient;
        }

        @Override
        public String getIssue(String issueKey) {
            return jiraApiClient.getIssue(issueKey)
                    .map(issue -> {
                        try {
                            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(issue);
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to serialize Jira issue", e);
                            return issue.getKey() + ": " + (issue.getFields() != null ? issue.getFields().toString() : "");
                        }
                    })
                    .orElse("Задача не найдена или Jira не настроена: " + issueKey);
        }

        @Override
        public String getRelatedIssues(String issueKey) {
            return "Связанные задачи (getRelatedIssues) пока не реализованы. Используйте getJiraIssue(\"" + issueKey + "\") для полей задачи.";
        }

        @Override
        public String getRemoteLinks(String issueKey) {
            return "Удалённые ссылки (getRemoteLinks) пока не реализованы.";
        }

        @Override
        public String getConfluencePage(String pageIdOrKey) {
            if (confluenceApiClient == null) {
                return "Confluence не настроен. Задайте app.confluence.base-url, app.confluence.login и app.confluence.password.";
            }
            return confluenceApiClient.getPage(pageIdOrKey, "body.storage,version")
                    .map(page -> {
                        try {
                            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(page);
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to serialize Confluence page", e);
                            return page.getTitle() + "\n" + (page.getBody() != null && page.getBody().getStorage() != null
                                    ? page.getBody().getStorage().getValue() : "");
                        }
                    })
                    .orElse("Страница Confluence не найдена или ошибка: " + pageIdOrKey);
        }

        @Override
        public String searchConfluence(String query) {
            return "Поиск Confluence (searchConfluence) пока не реализован.";
        }

        @Override
        public String searchByJql(String jql, int limit) {
            if (limit <= 0) {
                limit = 50;
            }
            return jiraApiClient.searchByJQL(jql, limit)
                    .map(result -> {
                        try {
                            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to serialize SearchResult", e);
                            return "total=" + result.getTotal() + ", issues=" + (result.getIssues() != null ? result.getIssues().size() : 0);
                        }
                    })
                    .orElse("Поиск по JQL не выполнен (пустой ответ или Jira не настроена).");
        }

        @Override
        public String getJiraTasksInCurrentSprint() {
            if (projectCode == null || projectCode.isEmpty()) {
                return "project-code не задан. Укажите app.jira.project-code для поиска задач текущего спринта.";
            }
            String jql = "project = " + projectCode + " AND Sprint in openSprints()";
            return searchByJql(jql, 50);
        }
    }
}
