package com.example.portal.agents.iconix.entity;

import com.example.portal.agents.iconix.model.WorkflowStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "workflow_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSession {
    
    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;
    
    @Column(name = "narrative", columnDefinition = "TEXT")
    private String narrative;
    
    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;
    
    @Column(name = "context_state", columnDefinition = "TEXT")
    private String contextStateJson; // JSON сериализация ctx.state
    
    @Column(name = "logs", columnDefinition = "TEXT")
    private String logsJson; // JSON сериализация ctx.logs
    
    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson; // JSON сериализация OrchestratorPlan
    
    @Column(name = "current_step_index")
    private int currentStepIndex;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private WorkflowStatus status;
    
    @Column(name = "user_review_data", columnDefinition = "TEXT")
    private String userReviewData; // JSON с данными для ревью (issues, artifacts и т.д.)

    /** Имя папки с сгенерированной документацией (относительно базового пути). Если не null — показываем кнопку «Документация». */
    @Column(name = "documentation_folder_name", length = 512)
    private String documentationFolderName;

    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

