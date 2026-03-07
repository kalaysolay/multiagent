package com.example.workflow.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO ответа Jira REST API GET /rest/api/2/search.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private List<JiraIssue> issues;
    private Integer startAt;
    private Integer maxResults;
    private Integer total;

    public SearchResult() {
    }

    public SearchResult(List<JiraIssue> issues, Integer startAt, Integer maxResults, Integer total) {
        this.issues = issues;
        this.startAt = startAt;
        this.maxResults = maxResults;
        this.total = total;
    }

    public List<JiraIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<JiraIssue> issues) {
        this.issues = issues;
    }

    public Integer getStartAt() {
        return startAt;
    }

    public void setStartAt(Integer startAt) {
        this.startAt = startAt;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
