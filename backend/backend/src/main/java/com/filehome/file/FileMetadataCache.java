package com.filehome.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.filehome.config.AppProperties;
import com.filehome.file.FileService.FileTreeNode;

@Component
public class FileMetadataCache {

    private static final Logger log =
            LoggerFactory.getLogger(FileMetadataCache.class);
    private static final String KEY_PREFIX = "filehome:metadata:";
    private static final Duration VERSION_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration fileListTtl;
    private final Duration treeTtl;
    private final Duration detailsTtl;
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final AtomicLong retryAfterMillis = new AtomicLong();

    public FileMetadataCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            AppProperties properties) {
        this.redis = redis;
        SimpleModule timeModule = new SimpleModule();
        timeModule.addSerializer(Instant.class, ToStringSerializer.instance);
        timeModule.addDeserializer(Instant.class, new InstantDeserializer());
        this.objectMapper = objectMapper.copy().registerModule(timeModule);
        this.fileListTtl = ttl(properties.cache().fileListSeconds());
        this.treeTtl = ttl(properties.cache().treeSeconds());
        this.detailsTtl = ttl(properties.cache().detailsSeconds());
    }

    public FileListResponse getList(
            UUID userId,
            String fingerprint,
            Supplier<FileListResponse> loader) {
        return getOrLoad(
                "list",
                userId,
                fingerprint,
                objectMapper.getTypeFactory().constructType(FileListResponse.class),
                fileListTtl,
                loader);
    }

    public List<FileTreeNode> getTree(
            UUID userId,
            UUID parentId,
            Supplier<List<FileTreeNode>> loader) {
        return getOrLoad(
                "tree",
                userId,
                parentId == null ? "root" : parentId.toString(),
                objectMapper.getTypeFactory().constructCollectionType(
                        List.class,
                        FileTreeNode.class),
                treeTtl,
                loader);
    }

    public FileView getDetails(
            UUID userId,
            UUID entryId,
            Supplier<FileView> loader) {
        return getOrLoad(
                "details",
                userId,
                entryId.toString(),
                objectMapper.getTypeFactory().constructType(FileView.class),
                detailsTtl,
                loader);
    }

    public List<FileView> getRecent(
            UUID userId,
            int limit,
            Supplier<List<FileView>> loader) {
        return getFileViews(
                "recent",
                userId,
                Integer.toString(limit),
                loader);
    }

    public List<FileView> getStarred(
            UUID userId,
            Supplier<List<FileView>> loader) {
        return getFileViews("starred", userId, "all", loader);
    }

    public void invalidateAfterCommit(UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            invalidate(userId);
                        }
                    });
            return;
        }
        invalidate(userId);
    }

    private void invalidate(UUID userId) {
        if (System.currentTimeMillis() < retryAfterMillis.get()) {
            return;
        }
        try {
            redis.opsForValue().set(
                    versionKey(userId),
                    UUID.randomUUID().toString(),
                    VERSION_TTL);
            retryAfterMillis.set(0);
        } catch (RuntimeException exception) {
            logFailure(exception);
        }
    }

    private List<FileView> getFileViews(
            String namespace,
            UUID userId,
            String discriminator,
            Supplier<List<FileView>> loader) {
        return getOrLoad(
                namespace,
                userId,
                discriminator,
                objectMapper.getTypeFactory().constructCollectionType(
                        List.class,
                        FileView.class),
                fileListTtl,
                loader);
    }

    private <T> T getOrLoad(
            String namespace,
            UUID userId,
            String discriminator,
            JavaType type,
            Duration ttl,
            Supplier<T> loader) {
        if (ttl.isZero()) {
            return loader.get();
        }
        if (System.currentTimeMillis() < retryAfterMillis.get()) {
            return loader.get();
        }

        String cacheKey = null;
        try {
            String version = currentVersion(userId);
            if (version != null) {
                cacheKey = dataKey(namespace, userId, version, discriminator);
                String json = redis.opsForValue().get(cacheKey);
                if (json != null) {
                    return objectMapper.readValue(json, type);
                }
            }
            retryAfterMillis.set(0);
        } catch (Exception exception) {
            logFailure(exception);
            cacheKey = null;
        }

        T value = loader.get();
        if (cacheKey == null) {
            return value;
        }
        try {
            redis.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(value),
                    ttl);
            retryAfterMillis.set(0);
        } catch (Exception exception) {
            logFailure(exception);
        }
        return value;
    }

    private String currentVersion(UUID userId) {
        String key = versionKey(userId);
        String version = redis.opsForValue().get(key);
        if (version != null) {
            return version;
        }
        String candidate = UUID.randomUUID().toString();
        redis.opsForValue().setIfAbsent(key, candidate, VERSION_TTL);
        return redis.opsForValue().get(key);
    }

    private String dataKey(
            String namespace,
            UUID userId,
            String version,
            String discriminator) {
        return KEY_PREFIX
                + namespace
                + ":"
                + userId
                + ":"
                + version
                + ":"
                + fingerprint(discriminator);
    }

    private String versionKey(UUID userId) {
        return KEY_PREFIX + "version:" + userId;
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Duration ttl(long seconds) {
        return seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    private void logFailure(Exception exception) {
        retryAfterMillis.set(System.currentTimeMillis() + 5_000);
        if (failureLogged.compareAndSet(false, true)) {
            log.warn(
                    "Redis metadata cache unavailable; using MySQL fallback",
                    exception);
        } else if (log.isDebugEnabled()) {
            log.debug("Redis metadata cache operation failed", exception);
        }
    }

    private static final class InstantDeserializer
            extends StdDeserializer<Instant> {

        private InstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(
                com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context)
                throws java.io.IOException {
            return Instant.parse(parser.getValueAsString());
        }
    }

}
