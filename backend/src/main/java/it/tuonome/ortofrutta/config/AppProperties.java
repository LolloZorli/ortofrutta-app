package it.tuonome.ortofrutta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin) {

    public record Jwt(String secret, Duration expiration) {
    }

    public record Admin(String username, String password, boolean mustChangePassword) {
    }
}
