package dev.jordi.senda.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "senda.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
