package com.filehome.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.filehome.common.ApiException;

class RequestRateLimiterTest {

    @Test
    void rejectsRequestsAboveLimit() {
        RequestRateLimiter limiter = new RequestRateLimiter(null);

        assertDoesNotThrow(
                () -> limiter.check("login:127.0.0.1:test", 2, Duration.ofHours(1)));
        assertDoesNotThrow(
                () -> limiter.check("login:127.0.0.1:test", 2, Duration.ofHours(1)));
        ApiException exception = assertThrows(
                ApiException.class,
                () -> limiter.check("login:127.0.0.1:test", 2, Duration.ofHours(1)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status());
    }
}
