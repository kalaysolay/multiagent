package com.example.portal.agents.iconix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "use_case_scenarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UseCaseScenario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "request_id", length = 36, nullable = false)
    private String requestId;
    
    @Column(name = "use_case_alias", length = 255)
    private String useCaseAlias; // Алиас Use Case (например, "reviewGiftByOfficer")
    
    @Column(name = "use_case_name", length = 500)
    private String useCaseName; // Название Use Case (например, "Проверить подарок офицером")
    
    @Column(name = "scenario_content", columnDefinition = "TEXT", nullable = false)
    private String scenarioContent; // AsciiDoc сценарий
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
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

