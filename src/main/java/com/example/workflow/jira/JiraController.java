package com.example.workflow.jira;

import com.example.workflow.ToolCallingChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Ручка для доступа к задаче Jira и агентам (анализ по ключу задачи).
 */
@RestController
@RequestMapping("/api/jira")
@RequiredArgsConstructor
public class JiraController {

    private final JiraApiClient jiraApiClient;
    private final ToolCallingChatService toolCallingChatService;

    @GetMapping("/issue/{issueIdOrKey}")
    public ResponseEntity<JiraIssue> getIssue(@PathVariable String issueIdOrKey) {
        return jiraApiClient.getIssue(issueIdOrKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Анализ задачи по ключу через LLM (тул getJiraIssue). Возвращает текст ответа ассистента.
     */
    @PostMapping("/analyze/task")
    public ResponseEntity<Map<String, String>> analyzeTask(@RequestBody AnalyzeTaskRequest request) {
        if (request == null || request.issueKey() == null || request.issueKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Укажите номер задачи (issueKey)."));
        }
        try {
            String prompt = "Дай информацию по задаче Jira " + request.issueKey().trim()
                    + ": ключ, название, статус, описание; если есть связи с другими задачами — упомяни их.";
            ToolCallingChatService.ChatResult result = toolCallingChatService.processMessage(
                    prompt,
                    java.util.List.of(),
                    null,
                    null
            );
            return ResponseEntity.ok(Map.of("response", result.response() != null ? result.response() : ""));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Ошибка анализа"));
        }
    }

    public record AnalyzeTaskRequest(String issueKey) {}
}
