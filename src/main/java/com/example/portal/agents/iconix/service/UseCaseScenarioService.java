package com.example.portal.agents.iconix.service;

import com.example.portal.agents.iconix.entity.UseCaseScenario;
import com.example.portal.agents.iconix.repository.UseCaseScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для работы со сценариями Use Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UseCaseScenarioService {
    
    private final UseCaseScenarioRepository repository;
    
    /**
     * Сохранить сценарий для указанного requestId.
     * JPA и Jackson автоматически обработают экранирование при сериализации в JSON.
     */
    @Transactional
    public UseCaseScenario saveScenario(String requestId, String scenarioContent) {
        try {
            UseCaseScenario scenario = UseCaseScenario.builder()
                    .requestId(requestId)
                    .scenarioContent(scenarioContent != null ? scenarioContent : "")
                    .build();
            
            UseCaseScenario saved = repository.save(scenario);
            log.info("Saved scenario for requestId: {}, length: {}", requestId, 
                    scenarioContent != null ? scenarioContent.length() : 0);
            return saved;
        } catch (Exception e) {
            log.error("Failed to save scenario for requestId: {}", requestId, e);
            throw new RuntimeException("Failed to save scenario", e);
        }
    }
    
    /**
     * Сохранить сценарий для указанного requestId с привязкой к Use Case.
     */
    @Transactional
    public UseCaseScenario saveScenario(String requestId, String useCaseAlias, String useCaseName, String scenarioContent) {
        try {
            UseCaseScenario scenario = UseCaseScenario.builder()
                    .requestId(requestId)
                    .useCaseAlias(useCaseAlias)
                    .useCaseName(useCaseName)
                    .scenarioContent(scenarioContent != null ? scenarioContent : "")
                    .build();
            
            UseCaseScenario saved = repository.save(scenario);
            log.info("Saved scenario for requestId: {}, useCaseAlias: {}, useCaseName: {}, length: {}", 
                    requestId, useCaseAlias, useCaseName, 
                    scenarioContent != null ? scenarioContent.length() : 0);
            return saved;
        } catch (Exception e) {
            log.error("Failed to save scenario for requestId: {}, useCaseAlias: {}", requestId, useCaseAlias, e);
            throw new RuntimeException("Failed to save scenario", e);
        }
    }
    
    /**
     * Получить все сценарии для указанного requestId.
     */
    @Transactional(readOnly = true)
    public List<UseCaseScenario> getScenariosByRequestId(String requestId) {
        return repository.findByRequestIdOrderByCreatedAtDesc(requestId);
    }
    
    /**
     * Получить все сценарии для указанного requestId и useCaseAlias.
     */
    @Transactional(readOnly = true)
    public List<UseCaseScenario> getScenariosByRequestIdAndAlias(String requestId, String useCaseAlias) {
        return repository.findByRequestIdAndUseCaseAliasOrderByCreatedAtDesc(requestId, useCaseAlias);
    }
    
    /**
     * Получить последний сценарий для указанного requestId и useCaseAlias.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<UseCaseScenario> getLatestScenarioByRequestIdAndAlias(String requestId, String useCaseAlias) {
        return repository.findFirstByRequestIdAndUseCaseAliasOrderByCreatedAtDesc(requestId, useCaseAlias);
    }
}

