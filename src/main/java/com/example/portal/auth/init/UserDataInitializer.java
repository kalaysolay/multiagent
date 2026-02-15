package com.example.portal.auth.init;

import com.example.portal.auth.entity.User;
import com.example.portal.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Инициализатор пользователей.
 * При старте приложения создаёт пользователя admin/admin с ролью администратора,
 * если в БД нет ни одного пользователя.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.debug("Пользователи уже существуют, пропускаем создание admin");
            return;
        }

        log.info("=== Создание пользователя admin (роль ADMIN) ===");

        User admin = User.builder()
                .username(DEFAULT_ADMIN_USERNAME)
                .password(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .enabled(true)
                .isAdmin(true)
                .createdAt(Instant.now())
                .build();

        userRepository.save(admin);
        log.info("Пользователь admin/admin создан (is_admin=true)");
    }
}
