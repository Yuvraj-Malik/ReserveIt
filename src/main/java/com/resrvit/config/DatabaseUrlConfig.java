package com.resrvit.config;

import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Render (and Heroku-style hosts) provide DATABASE_URL as postgres://user:pass@host:port/db.
 * Spring needs a JDBC URL, so convert it before the datasource is configured.
 * Registered in META-INF/spring.factories.
 */
public class DatabaseUrlConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String raw = env.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank() || env.getProperty("JDBC_DATABASE_URL") != null) return;
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return;
        URI uri = URI.create(raw);
        Map<String, Object> props = new HashMap<>();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        props.put("JDBC_DATABASE_URL", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath());
        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            props.put("JDBC_DATABASE_USERNAME", parts[0]);
            if (parts.length > 1) props.put("JDBC_DATABASE_PASSWORD", parts[1]);
        }
        env.getPropertySources().addFirst(new MapPropertySource("renderDatabaseUrl", props));
    }
}
