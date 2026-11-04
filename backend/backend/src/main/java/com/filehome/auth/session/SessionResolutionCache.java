package com.filehome.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filehome.config.AppProperties;

@Component
public class SessionResolutionCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SessionResolutionCache.class);
    private static final String SESSION_PREFIX = "filehome:auth:session:";
    private static final String REVOKED_PREFIX = "filehome:auth:revoked:";
    private static final long FAILURE_COOLDOWN_MILLIS = 5_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration maximumTtl;
    private final AtomicLong retryAfterMillis = new AtomicLong();
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public SessionResolutionCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            AppProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        long seconds = properties.auth().sessionCacheSeconds();
        this.maximumTtl = seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    public Optional<AuthSessionAccountRow> get(
            String tokenHashHex,
            Instant now) {
        if (maximumTtl.isZero() || unavailable()) {
            return Optional.empty();
        }
        try {
            List<String> values = redis.opsForValue().multiGet(List.of(
                    sessionKey(tokenHashHex),
                    revokedKey(tokenHashHex)));
            retryAfterMillis.set(0);
            if (values == null
                    || values.size() != 2
                    || values.get(0) == null
                    || values.get(1) != null) {
                return Optional.empty();
            }
            CachedSession cached = objectMapper.readValue(
                    values.get(0),
                    CachedSession.class);
            if (cached.sessionExpiresAtEpochMillis() <= now.toEpochMilli()) {
                return Optional.empty();
            }
            return Optional.of(cached.toRow());
        } catch (Exception exception) {
            logFailure(exception);
            return Optional.empty();
        }
    }

    public void put(
            String tokenHashHex,
            AuthSessionAccountRow row,
            Instant now) {
        if (maximumTtl.isZero() || unavailable()) {
            return;
        }
        Duration ttl = ttlUntil(row.sessionExpiresAt(), now);
        if (ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(
                    sessionKey(tokenHashHex),
                    objectMapper.writeValueAsString(CachedSession.from(row)),
                    ttl);
            retryAfterMillis.set(0);
        } catch (Exception exception) {
            logFailure(exception);
        }
    }

    public void revoke(String tokenHashHex, Instant expiresAt, Instant now) {
        Duration ttl = revocationTtlUntil(expiresAt, now);
        if (ttl.isZero()) {
            return;
        }
        if (unavailable()) {
            return;
        }
        try {
            redis.opsForValue().set(revokedKey(tokenHashHex), "1", ttl);
            redis.delete(sessionKey(tokenHashHex));
            retryAfterMillis.set(0);
        } catch (RuntimeException exception) {
            logFailure(exception);
        }
    }

    public void invalidate(String tokenHashHex) {
        if (unavailable()) {
            return;
        }
        try {
            redis.delete(sessionKey(tokenHashHex));
            retryAfterMillis.set(0);
        } catch (RuntimeException exception) {
            logFailure(exception);
        }
    }

    private boolean unavailable() {
        return System.currentTimeMillis() < retryAfterMillis.get();
    }

    private Duration ttlUntil(Instant expiresAt, Instant now) {
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.compareTo(maximumTtl) > 0 ? maximumTtl : remaining;
    }

    private Duration revocationTtlUntil(Instant expiresAt, Instant now) {
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }

    private String sessionKey(String tokenHashHex) {
        return SESSION_PREFIX + tokenHashHex;
    }

    private String revokedKey(String tokenHashHex) {
        return REVOKED_PREFIX + tokenHashHex;
    }

    private void logFailure(Exception exception) {
        retryAfterMillis.set(
                System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS);
        if (failureLogged.compareAndSet(false, true)) {
            LOGGER.warn(
                    "Redis session cache unavailable; using MySQL fallback",
                    exception);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Redis session cache operation failed", exception);
        }
    }
}
