package com.jereplatform.platform.identity;

import com.jereplatform.kernel.identity.api.SessionGrant;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
public class RefreshCookieService {

    public static final String COOKIE_NAME = "jp_refresh";
    public static final String INTENT_HEADER = "X-Refresh-Intent";
    public static final String INTENT_VALUE = "rotate";
    private static final String COOKIE_PATH = "/api/auth";

    private final SecurityRuntimeProperties properties;
    private final Clock clock;

    public RefreshCookieService(SecurityRuntimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }

    public void requireIntent(HttpServletRequest request) {
        if (!INTENT_VALUE.equals(request.getHeader(INTENT_HEADER))) {
            throw new RefreshIntentRequiredException();
        }
    }

    public void write(HttpHeaders headers, SessionGrant grant) {
        var maxAge = Duration.between(clock.instant(), grant.refreshExpiresAt());
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie
            .from(COOKIE_NAME, grant.refreshToken())
            .httpOnly(true)
            .secure(properties.refreshCookieSecure())
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build()
            .toString());
    }

    public void clear(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie
            .from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(properties.refreshCookieSecure())
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .build()
            .toString());
    }
}
