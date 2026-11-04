package com.filehome.upload;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.filehome.common.ApiException;

@Component
public class UploadPartLock {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                      return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class);

    private final StringRedisTemplate redis;

    public UploadPartLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String acquire(UUID uploadId, int partNumber) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    key(uploadId, partNumber),
                    token,
                    LOCK_TTL);
            if (!Boolean.TRUE.equals(acquired)) {
                throw ApiException.conflict(
                        "PART_BUSY",
                        "\u8be5\u5206\u7247\u6b63\u5728\u4e0a\u4f20\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
            }
            return token;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ApiException.serviceUnavailable(
                    "PART_LOCK_UNAVAILABLE",
                    "\u5206\u7247\u9501\u670d\u52a1\u6682\u4e0d\u53ef\u7528");
        }
    }

    public void release(UUID uploadId, int partNumber, String token) {
        if (token == null) {
            return;
        }
        try {
            redis.execute(
                    RELEASE_SCRIPT,
                    List.of(key(uploadId, partNumber)),
                    token);
        } catch (RuntimeException ignored) {
            // The short TTL guarantees eventual release if Redis is interrupted.
        }
    }

    private String key(UUID uploadId, int partNumber) {
        return "filehome:upload:part-lock:"
                + uploadId
                + ":"
                + partNumber;
    }
}
