package com.example.workflow.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты для JiraConfluenceTools: searchByJql, getJiraTasksInCurrentSprint (делегирование и валидация).
 */
@ExtendWith(MockitoExtension.class)
class JiraConfluenceToolsTest {

    @Mock
    private JiraConfluenceTools.JiraConfluenceApi jiraConfluenceApi;

    private JiraConfluenceTools tools;

    @BeforeEach
    void setUp() {
        tools = new JiraConfluenceTools();
        ReflectionTestUtils.setField(tools, "jiraConfluenceApi", jiraConfluenceApi);
    }

    @Test
    @DisplayName("searchByJql returns error when API is null")
    void searchByJql_returnsNotConfigured_whenApiNull() {
        ReflectionTestUtils.setField(tools, "jiraConfluenceApi", null);

        String result = tools.searchByJql("project = X", 10);

        assertThat(result).contains("не настроена");
    }

    @Test
    @DisplayName("searchByJql returns error when jql is blank")
    void searchByJql_returnsError_whenJqlBlank() {
        String result = tools.searchByJql("", 10);

        assertThat(result).contains("JQL");
        assertThat(result).contains("укажите");
    }

    @Test
    @DisplayName("searchByJql delegates to API and returns result")
    void searchByJql_delegatesToApi_andReturnsResult() {
        when(jiraConfluenceApi.searchByJql(eq("project = PROJ"), anyInt())).thenReturn("{\"total\":5}");

        String result = tools.searchByJql("project = PROJ", 20);

        assertThat(result).isEqualTo("{\"total\":5}");
        verify(jiraConfluenceApi).searchByJql("project = PROJ", 20);
    }

    @Test
    @DisplayName("getJiraTasksInCurrentSprint returns not configured when API is null")
    void getJiraTasksInCurrentSprint_returnsNotConfigured_whenApiNull() {
        ReflectionTestUtils.setField(tools, "jiraConfluenceApi", null);

        String result = tools.getJiraTasksInCurrentSprint();

        assertThat(result).contains("не настроена");
    }

    @Test
    @DisplayName("getJiraTasksInCurrentSprint delegates to API and returns result")
    void getJiraTasksInCurrentSprint_delegatesToApi_andReturnsResult() {
        when(jiraConfluenceApi.getJiraTasksInCurrentSprint()).thenReturn("{\"issues\":[]}");

        String result = tools.getJiraTasksInCurrentSprint();

        assertThat(result).isEqualTo("{\"issues\":[]}");
        verify(jiraConfluenceApi).getJiraTasksInCurrentSprint();
    }
}
