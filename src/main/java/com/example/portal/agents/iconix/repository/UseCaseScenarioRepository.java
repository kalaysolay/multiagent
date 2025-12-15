package com.example.portal.agents.iconix.repository;

import com.example.portal.agents.iconix.entity.UseCaseScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UseCaseScenarioRepository extends JpaRepository<UseCaseScenario, UUID> {
    
    /**
     * Найти все сценарии для указанного requestId, отсортированные по дате создания (от новых к старым).
     */
    List<UseCaseScenario> findByRequestIdOrderByCreatedAtDesc(String requestId);
}

