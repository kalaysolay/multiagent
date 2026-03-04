package com.example.workflow.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO ответа Jira REST API GET /rest/api/2/issue/{issueIdOrKey}.
 * fields — полный объект полей (включая customfield_*, issuetype, status, issuelinks и т.д.).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {
    private String id;
    private String key;
    private String self;
    /** expand — строка, при необходимости можно добавить поле и игнорировать. */
    private String expand;
    /** Все поля задачи: стандартные и кастомные, вложенные объекты/массивы. */
    private Map<String, Object> fields;
}
