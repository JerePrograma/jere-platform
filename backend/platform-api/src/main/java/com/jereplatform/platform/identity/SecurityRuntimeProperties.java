package com.jereplatform.platform.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.security")
public record SecurityRuntimeProperties(
    String jwtSecret,
    Duration accessTokenLifetime,
    Duration refreshTokenLifetime,
    boolean refreshCookieSecure,
    String bootstrapToken
) {
}
