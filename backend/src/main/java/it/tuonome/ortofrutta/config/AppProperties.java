package it.tuonome.ortofrutta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, PasswordReset passwordReset, Notifications notifications) {

    public record Jwt(String secret, Duration expiration) {
    }

    public record Admin(String username, String password, boolean mustChangePassword) {
    }

    public record PasswordReset(Duration tokenTtl, String baseUrl) {
    }

    public record Notifications(String recipientEmails) {
    }
}
