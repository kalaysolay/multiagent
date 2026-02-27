package com.example.portal.agents.iconix.service;

import com.example.portal.agents.iconix.entity.UseCaseMvc;
import com.example.portal.agents.iconix.repository.UseCaseMvcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для работы с декомпозированными MVC-диаграммами по Use Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UseCaseMvcService {

    private final UseCaseMvcRepository repository;

    @Transactional
    public UseCaseMvc save(String requestId, String useCaseAlias, String useCaseName, String mvcPlantuml) {
        UseCaseMvc entity = UseCaseMvc.builder()
                .requestId(requestId)
                .useCaseAlias(useCaseAlias)
                .useCaseName(useCaseName)
                .mvcPlantuml(mvcPlantuml != null ? mvcPlantuml : "")
                .build();
        UseCaseMvc saved = repository.save(entity);
        log.info("Saved MVC for requestId: {}, useCaseAlias: {}, useCaseName: {}, length: {}",
                requestId, useCaseAlias, useCaseName, mvcPlantuml != null ? mvcPlantuml.length() : 0);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<UseCaseMvc> getByRequestId(String requestId) {
        return repository.findByRequestIdOrderByCreatedAtDesc(requestId);
    }

    @Transactional(readOnly = true)
    public List<UseCaseMvc> getByRequestIdAndAlias(String requestId, String useCaseAlias) {
        return repository.findByRequestIdAndUseCaseAliasOrderByCreatedAtDesc(requestId, useCaseAlias);
    }
}
