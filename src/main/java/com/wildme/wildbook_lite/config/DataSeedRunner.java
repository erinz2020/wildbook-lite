package com.wildme.wildbook_lite.config;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.Role;
import com.wildme.wildbook_lite.auth.User;
import com.wildme.wildbook_lite.auth.UserRepository;

/**
 * Runs once on application startup, AFTER the Spring context is fully
 * initialised but BEFORE we accept requests. If no users exist yet,
 * seed one ADMIN so a fresh dev environment is usable.
 *
 * ApplicationRunner vs CommandLineRunner vs @PostConstruct:
 *
 *  - @PostConstruct: per-bean, runs as soon as that bean is built.
 *    Too early — DB might not be migrated, other beans not ready.
 *
 *  - CommandLineRunner / ApplicationRunner: run AFTER full context start
 *    completes. ApplicationArguments gives parsed --foo=bar nicely;
 *    CommandLineRunner just gets String[] args.
 *
 *  - @EventListener(ApplicationReadyEvent.class): fires last, after the
 *    web server is bound to its port. Use when you want "fully open for
 *    business" semantics.
 *
 * Why @Profile and @ConditionalOnProperty BOTH:
 *  - Don't seed in production, ever.
 *  - Give devs an off-switch for fast restarts when they don't want it.
 */
@Component
@Profile({"dev", "default"})
@ConditionalOnProperty(value = "app.seed.enabled", matchIfMissing = true)
public class DataSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeedRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.debug("[seed] users table is non-empty — skipping seed");
            return;
        }
        User admin = new User("admin", "admin@wildbook.local",
                              passwordEncoder.encode("admin12345"));
        admin.setRoles(EnumSet.of(Role.ADMIN, Role.RESEARCHER));
        userRepository.save(admin);
        log.warn("[seed] created default admin user 'admin' / 'admin12345' — CHANGE BEFORE DEPLOY");
    }
}
