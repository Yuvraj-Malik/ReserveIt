package com.resrvit.config;

import com.resrvit.domain.Role;
import com.resrvit.domain.UserStatus;
import com.resrvit.repo.LabRepository;
import com.resrvit.repo.UserRepository;
import com.resrvit.service.AccountService;
import com.resrvit.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * On start: loads the demo campus ({@link DemoData}) once when RESRVIT_SEED is on, alongside any existing data
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
    private final LabRepository labs;
    private final TransactionTemplate tx;

    @Value("${resrvit.seed-demo-data:false}")
    private boolean seed;

    @Value("${resrvit.bootstrap-admin-email:}")
    private String adminEmail;

    @Value("${resrvit.bootstrap-admin-password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        // Load the demo campus once: when seeding is on and its labs aren't there yet. Existing accounts are kept.
        if (seed && labs.findByCodeIgnoreCase("ELAB").isEmpty()) {
            try {
                tx.executeWithoutResult(s -> demoData.load());
                log.info("Demo data loaded. Sign in as admin@{} / password (see README for every role).", settings.emailDomain());
            } catch (RuntimeException e) {
                log.error("Demo data could not be loaded; continuing without it.", e);
            }
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
