package com.resrvit.config;

import com.resrvit.domain.User;
import com.resrvit.domain.UserStatus;
import com.resrvit.repo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> {
            User u = users.findByEmailIgnoreCase(username.trim())
                    .orElseThrow(() -> new UsernameNotFoundException("No account for " + username));
            boolean enabled = u.getStatus() == UserStatus.ACTIVE;
            return org.springframework.security.core.userdetails.User.withUsername(u.getEmail())
                    .password(u.getPasswordHash())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))
                    .disabled(!enabled)
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> google,
                                           GoogleUserService googleUsers,
                                           com.resrvit.service.SettingsService settings) throws Exception {
        ClientRegistrationRepository registrations = google.getIfAvailable();
        if (registrations != null) {
            // Ask Google to show only college accounts (hd hint); GoogleUserService enforces it.
            DefaultOAuth2AuthorizationRequestResolver resolver =
                    new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(c -> c.additionalParameters(p -> {
                p.put("hd", settings.emailDomain());
                p.put("prompt", "select_account");
            }));
            http.oauth2Login(o -> o
                    .loginPage("/login")
                    .authorizationEndpoint(a -> a.authorizationRequestResolver(resolver))
                    .userInfoEndpoint(u -> u.oidcUserService(googleUsers))
                    .defaultSuccessUrl("/", true)
                    .failureHandler((req, res, ex) -> {
                        String code = ex instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oe
                                ? oe.getError().getErrorCode() : "google";
                        res.sendRedirect("/login?error=" + (code.startsWith("google_") || code.equals("inactive") ? code : "google"));
                    }));
        }
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/error",
                        "/login", "/signup", "/signup/**", "/verify", "/verify/**", "/actuator/health", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/admin/**", "/reports/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/announcements/**").hasAnyRole("SUPER_ADMIN", "GD_ADMIN", "SOCIETY_ADMIN", "LAB_ASSISTANT")
                .requestMatchers("/gd/**").hasAnyRole("GD_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/society-admin/**").hasAnyRole("SOCIETY_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/faculty/**").hasAnyRole("FACULTY", "SUPER_ADMIN")
                .requestMatchers("/assistant/**").hasAnyRole("LAB_ASSISTANT", "SUPER_ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler((req, res, ex) -> {
                    String code = ex instanceof org.springframework.security.authentication.DisabledException ? "inactive" : "bad";
                    res.sendRedirect("/login?error=" + code);
                })
                .permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout").permitAll())
            .csrf(c -> c.ignoringRequestMatchers("/h2-console/**", "/ws/**"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
}
