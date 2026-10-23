package com.resrvit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Google sign-in, switched on only when GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set.
 * Redirect URI to register in Google Cloud Console: {base-url}/login/oauth2/code/google
 */
@Configuration
public class GoogleAuthConfig {

    @Bean
    @ConditionalOnExpression("!'${resrvit.google.client-id:}'.isBlank()")
    public ClientRegistrationRepository googleClientRegistrations(@Value("${resrvit.google.client-id}") String id,
                                                                  @Value("${resrvit.google.client-secret}") String secret) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(id.trim())
                .clientSecret(secret.trim())
                .scope("openid", "email", "profile")
                .build());
    }
}
