package com.resrvit.config;

import com.resrvit.domain.Role;
import com.resrvit.domain.UserStatus;
import com.resrvit.repo.UserRepository;
import com.resrvit.service.AccountService;
import com.resrvit.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On start: loads the demo campus ({@link DemoData}) into an empty database when RESRVIT_SEED is on
 * (the default locally), and creates the first super admin from ADMIN_EMAIL / ADMIN_PASSWORD if none exists.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final AccountService accounts;
    private final SettingsService settings;
    private final DemoData demoData;

    @Value("${resrvit.seed-demo-data:false}")
    private boolean seed;

    @Value("${resrvit.bootstrap-admin-email:}")
    private String adminEmail;

    @Value("${resrvit.bootstrap-admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seed && users.count() == 0) {
            demoData.load();
            log.info("Demo data loaded. Sign in as admin@{} / password (see README for every role).", settings.emailDomain());
        }
        boolean hasSuperAdmin = !users.findByRoleAndStatus(Role.SUPER_ADMIN, UserStatus.ACTIVE).isEmpty();
        if (!hasSuperAdmin && adminEmail != null && !adminEmail.isBlank() && adminPassword != null && adminPassword.length() >= 8) {
            accounts.bootstrapSuperAdmin(adminEmail, adminPassword);
            log.info("Created super admin {}", adminEmail);
        } else if (!hasSuperAdmin) {
            log.warn("No super admin exists. Set ADMIN_EMAIL and ADMIN_PASSWORD (8+ characters) and restart.");
        }
    }
}
