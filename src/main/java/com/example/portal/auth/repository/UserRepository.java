package com.example.portal.auth.repository;

import com.example.portal.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * Находит пользователя по username (только активных, не удалённых).
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Находит пользователя по username, игнорируя статус удаления.
     */
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
    
    /**
     * Проверяет существование пользователя с указанным username (только среди активных).
     */
    boolean existsByUsername(String username);
    
    /**
     * Проверяет существование активного пользователя с указанным username.
     */
    boolean existsByUsernameAndDeletedAtIsNull(String username);
    
    /**
     * Находит все активные пользователи (deleted_at IS NULL).
     */
    java.util.List<User> findAllByDeletedAtIsNull();
    
    /**
     * Находит активного пользователя по ID.
     */
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
}
