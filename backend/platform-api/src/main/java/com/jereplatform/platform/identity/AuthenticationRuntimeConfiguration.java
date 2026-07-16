package com.jereplatform.platform.identity;

import com.jereplatform.kernel.identity.api.AuthenticationSessionPolicy;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(SecurityRuntimeProperties.class)
public class AuthenticationRuntimeConfiguration {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(AuthenticationRuntimeConfiguration.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock platformClock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthenticationSessionPolicy authenticationSessionPolicy(
        SecurityRuntimeProperties properties
    ) {
        return new AuthenticationSessionPolicy(
            properties.accessTokenLifetime(),
            properties.refreshTokenLifetime()
        );
    }

    @Bean
    JwtTokenService jwtTokenService(
        SecurityRuntimeProperties properties,
        Clock clock,
        AuthenticationSessionPolicy policy
    ) {
        return new JwtTokenService(resolveJwtSecret(properties.jwtSecret()), clock, policy);
    }

    private static byte[] resolveJwtSecret(String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            var bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 bytes");
            }
            return bytes;
        }

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        LOGGER.warn(
            "AUTH_JWT_SECRET is not configured; using an ephemeral development key: {}",
            Base64.getEncoder().encodeToString(random)
        );
        return random;
    }
}
