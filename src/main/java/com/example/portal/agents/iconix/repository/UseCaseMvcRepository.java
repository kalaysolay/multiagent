package com.example.portal.agents.iconix.repository;

import com.example.portal.agents.iconix.entity.UseCaseMvc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UseCaseMvcRepository extends JpaRepository<UseCaseMvc, UUID> {

    List<UseCaseMvc> findByRequestIdOrderByCreatedAtDesc(String requestId);

    List<UseCaseMvc> findByRequestIdAndUseCaseAliasOrderByCreatedAtDesc(String requestId, String useCaseAlias);

    Optional<UseCaseMvc> findFirstByRequestIdAndUseCaseAliasOrderByCreatedAtDesc(String requestId, String useCaseAlias);
}
