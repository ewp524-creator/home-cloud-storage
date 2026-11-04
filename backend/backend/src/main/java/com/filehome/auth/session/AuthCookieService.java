package com.filehome.auth.session;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.filehome.config.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthCookieService {

    public static final String SESSION_COOKIE = "FH_SESSION";
    public static final String CSRF_COOKIE = "FH_CSRF";

    private final boolean secure;

    public AuthCookieService(AppProperties properties) {
        this.secure = properties.auth().cookieSecure();
    }

    public void write(
            HttpServletResponse response,
            String sessionToken,
            String csrfToken,
            Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        if (maxAge.isNegative() || maxAge.isZero()) {
            maxAge = Duration.ofSeconds(1);
        }
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie(SESSION_COOKIE, sessionToken, maxAge, true));
        writeCsrf(response, csrfToken, maxAge);
    }

    public void writeCsrf(
            HttpServletResponse response,
            String csrfToken,
            Duration maxAge) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie(CSRF_COOKIE, csrfToken, maxAge, false));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie(SESSION_COOKIE, "", Duration.ZERO, true));
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie(CSRF_COOKIE, "", Duration.ZERO, false));
    }

    public String readSessionToken(HttpServletRequest request) {
        return readCookie(request, SESSION_COOKIE);
    }

    public String readCsrfToken(HttpServletRequest request) {
        return readCookie(request, CSRF_COOKIE);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue() == null ? "" : cookie.getValue();
            }
        }
        return "";
    }

    private String cookie(
            String name,
            String value,
            Duration maxAge,
            boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
