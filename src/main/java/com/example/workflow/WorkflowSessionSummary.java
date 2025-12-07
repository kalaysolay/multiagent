package com.example.workflow;

import com.example.portal.agents.iconix.entity.WorkflowSession;
import com.example.portal.agents.iconix.model.WorkflowStatus;

import java.time.Instant;

public record WorkflowSessionSummary(
        String requestId,
        WorkflowStatus status,
        String author,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowSessionSummary from(WorkflowSession session) {
        return new WorkflowSessionSummary(
                session.getRequestId(),
                session.getStatus(),
                "System", // TODO: добавить поле author в WorkflowSession
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}

