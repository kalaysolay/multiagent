package com.example.portal.agents.iconix.repository;

import com.example.portal.agents.iconix.entity.WorkflowSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowSessionRepository extends JpaRepository<WorkflowSession, String> {
    Optional<WorkflowSession> findByRequestId(String requestId);
}

