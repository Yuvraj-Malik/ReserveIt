package com.resrvit.service;

import com.resrvit.domain.User;
import com.resrvit.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository users;

    public Optional<User> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return Optional.empty();
        return users.findByEmailIgnoreCase(auth.getName());
    }

    public User get() {
        return find().orElseThrow(() -> new IllegalStateException("Not signed in"));
    }
}
