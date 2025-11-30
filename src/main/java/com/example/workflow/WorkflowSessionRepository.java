package com.example.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowSessionRepository extends JpaRepository<WorkflowSession, String> {
    Optional<WorkflowSession> findByRequestId(String requestId);
}

