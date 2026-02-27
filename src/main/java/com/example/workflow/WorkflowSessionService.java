package com.example.workflow;

import com.example.portal.agents.iconix.entity.WorkflowSession;
import com.example.portal.agents.iconix.repository.WorkflowSessionRepository;
import com.example.portal.agents.iconix.worker.Worker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSessionService {
    
    private final WorkflowSessionRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Transactional
    public WorkflowSession saveSession(Worker.Context ctx, com.example.portal.agents.iconix.model.OrchestratorPlan plan, 
                                      int currentStepIndex, com.example.portal.agents.iconix.model.WorkflowStatus status, 
                                      String userReviewData, Integer maxIterations) {
        try {
            WorkflowSession session = repository.findByRequestId(ctx.requestId)
                    .map(existing -> {
                        existing.setNarrative(ctx.narrativeEffective());
                        existing.setGoal(ctx.goal);
                        existing.setContextStateJson(serializeState(ctx.state));
                        existing.setLogsJson(serializeLogs(ctx.logs));
                        existing.setPlanJson(serializePlan(plan));
                        existing.setCurrentStepIndex(currentStepIndex);
                        existing.setStatus(status);
                        existing.setUserReviewData(userReviewData);
                        // existing.setMaxIterations(maxIterations);  // Закомментировано - рудимент
                        return existing;
                    })
                    .orElse(WorkflowSession.builder()
                            .requestId(ctx.requestId)
                            .narrative(ctx.narrativeEffective())
                            .goal(ctx.goal)
                            .contextStateJson(serializeState(ctx.state))
                            .logsJson(serializeLogs(ctx.logs))
                            .planJson(serializePlan(plan))
                            .currentStepIndex(currentStepIndex)
                            .status(status)
                            .userReviewData(userReviewData)
                            // .maxIterations(maxIterations)  // Закомментировано - рудимент
                            .build());
            
            return repository.save(session);
        } catch (Exception e) {
            log.error("Failed to save workflow session: {}", ctx.requestId, e);
            throw new RuntimeException("Failed to save workflow session", e);
        }
    }
    
    @Transactional(readOnly = true)
    public Optional<WorkflowSession> loadSession(String requestId) {
        return repository.findByRequestId(requestId);
    }
    
    @Transactional(readOnly = true)
    public List<WorkflowSessionSummary> getAllSessions() {
        return repository.findAll().stream()
                .sorted((a, b) -> {
                    Instant aTime = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.EPOCH;
                    Instant bTime = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.EPOCH;
                    return bTime.compareTo(aTime); // Сортировка по убыванию (новые сверху)
                })
                .map(WorkflowSessionSummary::from)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Собирает ядро артефактов (goal, narrative, plantuml, issues, useCaseModel, mvcDiagram, scenarios)
     * без служебных полей _status и _reviewData. Единый источник правды для структуры артефактов.
     *
     * @param goal              Исходная цель пользователя
     * @param narrativeEffective Эффективный нарратив (из контекста или override)
     * @param state             State контекста (plantuml, issues, narrativeIssues, useCaseModel, mvcDiagram, scenario)
     * @return Карта артефактов с предсказуемым порядком полей (LinkedHashMap)
     */
    public Map<String, Object> buildCoreArtifacts(String goal, String narrativeEffective, Map<String, Object> state) {
        Map<String, Object> artifacts = new LinkedHashMap<>();
        if (goal != null && !goal.isBlank()) {
            artifacts.put("goal", goal);
        }
        artifacts.put("narrative", narrativeEffective != null ? narrativeEffective : "");
        if (state != null && state.containsKey("plantuml")) {
            artifacts.put("plantuml", state.get("plantuml"));
        }
        if (state != null && state.containsKey("issues")) {
            artifacts.put("issues", state.get("issues"));
        }
        if (state != null && state.containsKey("narrativeIssues")) {
            artifacts.put("narrativeIssues", state.get("narrativeIssues"));
        }
        if (state != null && state.containsKey("useCaseModel")) {
            artifacts.put("useCaseModel", state.get("useCaseModel"));
        }
        if (state != null && state.containsKey("mvcDiagram")) {
            artifacts.put("mvcDiagram", state.get("mvcDiagram"));
        }
        if (state != null && state.containsKey("scenario")) {
            List<String> scenarios = List.of((String) state.get("scenario"));
            artifacts.put("scenarios", scenarios);
        }
        return artifacts;
    }

    /**
     * Получить данные сессии в формате WorkflowResponse для отображения на фронтенде.
     */
    @Transactional(readOnly = true)
    public com.example.portal.agents.iconix.model.WorkflowResponse getSessionData(String requestId) {
        WorkflowSession session = loadSession(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + requestId));
        
        Worker.Context ctx = restoreContext(session);
        com.example.portal.agents.iconix.model.OrchestratorPlan plan = restorePlan(session);
        
        Map<String, Object> artifacts = buildCoreArtifacts(
                session.getGoal(),
                ctx.narrativeEffective(),
                ctx.state
        );
        artifacts.put("_status", session.getStatus().toString());
        // Флаг и имя папки для кнопки «Документация» и предзаполнения формы обновления
        String docFolder = session.getDocumentationFolderName();
        artifacts.put("hasGeneratedDocs", docFolder != null && !docFolder.isBlank());
        if (docFolder != null && !docFolder.isBlank()) {
            artifacts.put("documentationFolderName", docFolder);
        }

        // Если есть данные для ревью, добавляем их
        if (session.getUserReviewData() != null && !session.getUserReviewData().isBlank()) {
            try {
                Map<String, Object> reviewData = objectMapper.readValue(
                        session.getUserReviewData(), 
                        new TypeReference<Map<String, Object>>() {}
                );
                artifacts.put("_reviewData", reviewData);
            } catch (Exception e) {
                log.warn("Failed to parse user review data for session: {}", requestId, e);
            }
        }
        
        return new com.example.portal.agents.iconix.model.WorkflowResponse(
                session.getRequestId(),
                plan,
                artifacts,
                ctx.logs
        );
    }
    
    public Worker.Context restoreContext(WorkflowSession session) {
        try {
            var ctx = new Worker.Context(
                    session.getRequestId(),
                    session.getNarrative() != null ? session.getNarrative() : "",
                    session.getGoal() != null ? session.getGoal() : ""
            );
            
            // Восстанавливаем state
            if (session.getContextStateJson() != null) {
                Map<String, Object> state = deserializeState(session.getContextStateJson());
                ctx.state.putAll(state);
            }
            
            // Восстанавливаем logs
            if (session.getLogsJson() != null) {
                List<String> logs = deserializeLogs(session.getLogsJson());
                ctx.logs.addAll(logs);
            }
            
            return ctx;
        } catch (Exception e) {
            log.error("Failed to restore context for session: {}", session.getRequestId(), e);
            throw new RuntimeException("Failed to restore context", e);
        }
    }
    
    public com.example.portal.agents.iconix.model.OrchestratorPlan restorePlan(WorkflowSession session) {
        try {
            if (session.getPlanJson() == null) {
                throw new IllegalStateException("Plan JSON is null for session: " + session.getRequestId());
            }
            return deserializePlan(session.getPlanJson());
        } catch (Exception e) {
            log.error("Failed to restore plan for session: {}", session.getRequestId(), e);
            throw new RuntimeException("Failed to restore plan", e);
        }
    }
    
    /**
     * Сохраняет имя папки сгенерированной документации для сессии.
     * Вызывается после успешной генерации файлов документации.
     */
    @Transactional
    public void setDocumentationFolderForSession(String requestId, String folderName) {
        WorkflowSession session = repository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + requestId));
        session.setDocumentationFolderName(folderName);
        repository.save(session);
    }

    @Transactional
    public void updateContextFromUserInput(String requestId, String narrative, String domainModel) {
        Optional<WorkflowSession> sessionOpt = repository.findByRequestId(requestId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + requestId);
        }
        
        WorkflowSession session = sessionOpt.get();
        
        // Обновляем narrative если передан
        // Данные приходят из JSON, Spring автоматически их парсит, оставляем как есть
        if (narrative != null && !narrative.isBlank()) {
            session.setNarrative(narrative);
        }
        
        // Обновляем domain model в state
        if (domainModel != null && !domainModel.isBlank()) {
            try {
                Map<String, Object> state = deserializeState(session.getContextStateJson());
                state.put("plantuml", domainModel);
                session.setContextStateJson(serializeState(state));
            } catch (Exception e) {
                log.error("Failed to update domain model in session: {}", requestId, e);
            }
        }
        
        repository.save(session);
    }
    
    private String serializeState(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Failed to serialize state", e);
            return "{}";
        }
    }
    
    private Map<String, Object> deserializeState(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize state", e);
            return new HashMap<>();
        }
    }
    
    private String serializeLogs(List<String> logs) {
        try {
            return objectMapper.writeValueAsString(logs);
        } catch (Exception e) {
            log.error("Failed to serialize logs", e);
            return "[]";
        }
    }
    
    private List<String> deserializeLogs(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize logs", e);
            return new ArrayList<>();
        }
    }
    
    private String serializePlan(com.example.portal.agents.iconix.model.OrchestratorPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            log.error("Failed to serialize plan", e);
            return "{}";
        }
    }
    
    private com.example.portal.agents.iconix.model.OrchestratorPlan deserializePlan(String json) {
        try {
            return objectMapper.readValue(json, com.example.portal.agents.iconix.model.OrchestratorPlan.class);
        } catch (Exception e) {
            log.error("Failed to deserialize plan", e);
            throw new RuntimeException("Failed to deserialize plan", e);
        }
    }
}

