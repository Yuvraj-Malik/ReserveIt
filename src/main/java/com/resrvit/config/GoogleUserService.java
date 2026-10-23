package com.resrvit.config;

import com.resrvit.domain.Role;
import com.resrvit.domain.User;
import com.resrvit.domain.UserStatus;
import com.resrvit.repo.UserRepository;
import com.resrvit.service.NotificationService;
import com.resrvit.service.SettingsService;
import com.resrvit.service.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Accepts only verified Google accounts on the college domain (checked on both the email and
 * Google's hosted-domain claim). A first-time Google user gets a student account that waits for
 * admin activation, like a normal signup, but without the email code since Google verified it.
 */
@Component
@RequiredArgsConstructor
public class GoogleUserService extends OidcUserService {

    private final UserRepository users;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final PasswordEncoder encoder;
    private final TimeService time;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);
        String domain = settings.emailDomain();
        String email = google.getEmail() == null ? "" : google.getEmail().toLowerCase(Locale.ROOT);
        Object hd = google.getClaims().get("hd");
        boolean verified = Boolean.TRUE.equals(google.getEmailVerified());
        if (!verified || !email.endsWith("@" + domain) || hd == null || !domain.equalsIgnoreCase(hd.toString())) {
            throw fail("google_domain");
        }
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u == null) {
            u = new User();
            u.setEmail(email);
            u.setFullName(google.getFullName() != null ? google.getFullName() : email.substring(0, email.indexOf('@')));
            u.setRole(Role.STUDENT);
            u.setStatus(UserStatus.PENDING_ACTIVATION);
            u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            u.setGoogleSub(google.getSubject());
            u.setCreatedAt(time.now());
            users.save(u);
            notifications.notifyRole(Role.SUPER_ADMIN, "New Google sign-up waiting for activation: " + u.getFullName()
                    + " (" + email + ")", "/admin/users?status=PENDING_ACTIVATION");
            throw fail("google_pending");
        }
        if (u.getGoogleSub() == null) {
            u.setGoogleSub(google.getSubject());
            users.save(u);
        }
        if (u.getStatus() == UserStatus.PENDING_VERIFICATION) {
            u.setStatus(UserStatus.PENDING_ACTIVATION);
            users.save(u);
        }
        if (u.getStatus() != UserStatus.ACTIVE) throw fail("inactive");
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())),
                google.getIdToken(), google.getUserInfo(), "email");
    }

    private static OAuth2AuthenticationException fail(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), code);
    }
}
