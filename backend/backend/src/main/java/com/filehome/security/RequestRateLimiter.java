package com.filehome.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.filehome.common.ApiException;

@Component
public class RequestRateLimiter {

    private static final int MAX_TRACKED_KEYS = 20_000;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                      redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
                    Long.class);

    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private volatile long redisRetryAfterMillis;

    public RequestRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String key, int limit, Duration duration) {
        if (limit <= 0 || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Rate limit must be positive");
        }
        long durationMillis = Math.max(1, duration.toMillis());
        long now = Instant.now().toEpochMilli();
        long bucket = now / durationMillis;
        long remainingMillis =
                durationMillis - Math.floorMod(now, durationMillis);
        String redisKey = "filehome:rate:" + key + ":" + bucket;

        Long count = null;
        if (redis != null && System.currentTimeMillis() >= redisRetryAfterMillis) {
            try {
                count = redis.execute(
                        INCREMENT_SCRIPT,
                        List.of(redisKey),
                        Long.toString(Math.max(1, remainingMillis)));
                redisRetryAfterMillis = 0;
            } catch (RuntimeException exception) {
                redisRetryAfterMillis = System.currentTimeMillis() + 5_000;
                count = null;
            }
        }
        if (count != null) {
            if (count > limit) {
                throw ApiException.tooManyRequests("璇锋眰杩囦簬棰戠箒锛岃绋嶅悗鍐嶈瘯");
            }
            return;
        }

        checkInMemory(key, limit, durationMillis, bucket);
    }

    private void checkInMemory(
            String key,
            int limit,
            long durationMillis,
            long bucket) {
        Window window = windows.compute(
                key,
                (ignored, current) -> current == null || current.bucket() != bucket
                        ? new Window(bucket, new AtomicInteger())
                        : current);
        if (window.count().incrementAndGet() > limit) {
            throw ApiException.tooManyRequests("请求过于频繁，请稍后再试");
        }
        sweepIfNeeded(bucket);
    }

    private void sweepIfNeeded(long currentBucket) {
        if (windows.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        windows.entrySet().removeIf(
                entry -> entry.getValue().bucket() < currentBucket - 2);
    }

    private record Window(long bucket, AtomicInteger count) {
    }
}
