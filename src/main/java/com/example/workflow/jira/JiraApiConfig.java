package com.example.workflow.jira;

import com.example.workflow.tools.JiraConfluenceTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Реализация JiraConfluenceApi через JiraApiClient. Подключается только при заданном app.jira.base-path.
 * Тул getJiraIssue в чате тогда возвращает данные из Jira; остальные методы — заглушка «пока не реализовано».
 */
@Configuration
@ConditionalOnProperty(name = "app.jira.base-path")
@Slf4j
public class JiraApiConfig {

    @Bean
    public JiraConfluenceTools.JiraConfluenceApi jiraConfluenceApi(JiraApiClient jiraApiClient) {
        return new JiraConfluenceApiImpl(jiraApiClient);
    }

    @RequiredArgsConstructor
    private static class JiraConfluenceApiImpl implements JiraConfluenceTools.JiraConfluenceApi {
        private final JiraApiClient jiraApiClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

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
            return "Confluence (getConfluencePage) пока не реализован.";
        }

        @Override
        public String searchConfluence(String query) {
            return "Поиск Confluence (searchConfluence) пока не реализован.";
        }
    }
}
