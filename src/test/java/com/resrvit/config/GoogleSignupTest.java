package com.resrvit.config;

import com.resrvit.domain.Role;
import com.resrvit.domain.UserStatus;
import com.resrvit.repo.NotificationRepository;
import com.resrvit.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * First-time Google sign-in must leave a PENDING_ACTIVATION account behind even though
 * loadUser throws "google_pending". Not @Transactional on purpose: like the real login,
 * there is no outer transaction, so a rollback would really delete the user.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoogleSignupTest {

    static final String EMAIL = "google.newbie@thapar.edu";

    @MockitoSpyBean GoogleUserService google;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    @AfterEach
    void cleanUp() {
        users.findByEmailIgnoreCase(EMAIL).ifPresent(users::delete);
    }

    private OidcUser fakeGoogleUser(String email, String hd) {
        OidcIdToken token = OidcIdToken.withTokenValue("fake")
                .subject("google-sub-123")
                .claim("email", email)
                .claim("email_verified", true)
                .claim("hd", hd)
                .claim("name", "Google Newbie")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token, "email");
    }

    @Test
    void firstGoogleSignInIsSavedAsPendingActivation() {
        doReturn(fakeGoogleUser(EMAIL, "thapar.edu")).when(google).fetchFromGoogle(any());

        assertThatThrownBy(() -> google.loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("google_pending");

        var saved = users.findByEmailIgnoreCase(EMAIL);
        assertThat(saved).as("account must survive the 'pending' exception").isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(saved.get().getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.get().getGoogleSub()).isEqualTo("google-sub-123");
    }

    @Test
    void outsideDomainIsRejectedAndNothingIsSaved() {
        doReturn(fakeGoogleUser("someone@gmail.com", "gmail.com")).when(google).fetchFromGoogle(any());

        assertThatThrownBy(() -> google.loadUser(null)).hasMessageContaining("google_domain");
        assertThat(users.findByEmailIgnoreCase("someone@gmail.com")).isEmpty();
    }
}
