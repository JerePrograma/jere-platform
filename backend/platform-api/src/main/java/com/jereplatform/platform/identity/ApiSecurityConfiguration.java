package com.jereplatform.platform.identity;

import com.jereplatform.kernel.identity.application.SessionValidationService;
import com.jereplatform.kernel.tenancy.application.TenantAccessService;
import com.jereplatform.platform.tenancy.TenantContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ApiSecurityConfiguration {

    @Bean
    AccessTokenAuthenticationFilter accessTokenAuthenticationFilter(
        JwtTokenService jwtTokenService,
        SessionValidationService sessionValidationService
    ) {
        return new AccessTokenAuthenticationFilter(jwtTokenService, sessionValidationService);
    }

    @Bean
    TenantContextFilter tenantContextFilter(TenantAccessService tenantAccessService) {
        return new TenantContextFilter(tenantAccessService);
    }

    @Bean
    FilterRegistrationBean<AccessTokenAuthenticationFilter> disableAccessTokenServletRegistration(
        AccessTokenAuthenticationFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> disableTenantContextServletRegistration(
        TenantContextFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
        HttpSecurity http,
        AccessTokenAuthenticationFilter accessTokenAuthenticationFilter,
        TenantContextFilter tenantContextFilter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> { })
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, failure) ->
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required"))
                .accessDeniedHandler((request, response, failure) ->
                    response.sendError(HttpStatus.FORBIDDEN.value(), "Access denied")))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/api/auth/**",
                    "/api/bootstrap/initialize"
                ).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(
                accessTokenAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .addFilterAfter(tenantContextFilter, AccessTokenAuthenticationFilter.class)
            .build();
    }
}
