package com.example.workflow.tools;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Заглушка Jira/Confluence API. Подключается, если вы ещё не зарегистрировали свой бин
 * {@link JiraConfluenceTools.JiraConfluenceApi}. Как только добавите стартер и создадите
 * реальную реализацию (например, вызовы ваших REST-ручек), зарегистрируйте её как @Bean —
 * заглушка не будет использоваться.
 */
@Configuration
public class JiraConfluenceApiStub {

    @Bean
    @ConditionalOnMissingBean(JiraConfluenceTools.JiraConfluenceApi.class)
    public JiraConfluenceTools.JiraConfluenceApi jiraConfluenceApiStub() {
        return new JiraConfluenceTools.JiraConfluenceApi() {
            @Override
            public String getIssue(String issueKey) {
                return "Jira/Confluence не настроены. Добавьте бин JiraConfluenceApi (ваш стартер) с реализацией getIssue.";
            }

            @Override
            public String getRelatedIssues(String issueKey) {
                return "Jira/Confluence не настроены. Реализуйте getRelatedIssues в вашем JiraConfluenceApi.";
            }

            @Override
            public String getRemoteLinks(String issueKey) {
                return "Jira/Confluence не настроены. Реализуйте getRemoteLinks в вашем JiraConfluenceApi.";
            }

            @Override
            public String getConfluencePage(String pageIdOrKey) {
                return "Jira/Confluence не настроены. Реализуйте getConfluencePage в вашем JiraConfluenceApi.";
            }

            @Override
            public String searchConfluence(String query) {
                return "Jira/Confluence не настроены. Реализуйте searchConfluence в вашем JiraConfluenceApi.";
            }
        };
    }
}
