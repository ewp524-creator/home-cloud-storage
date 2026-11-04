package com.filehome.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Auth auth,
        Upload upload,
        Storage storage,
        Share share,
        Cors cors,
        Cache cache) {

    public record Jwt(String secret, long accessTokenMinutes) {
    }

    public record Auth(
            long sessionHours,
            boolean cookieSecure,
            long sessionCacheSeconds) {
    }

    public record Upload(
            long partSizeBytes,
            long maxFileSizeBytes,
            long sessionHours,
            int maxActiveSessions) {
    }

    public record Storage(
            String endpoint,
            String consoleUrl,
            String accessKey,
            String secretKey,
            String bucket,
            String dataRoot) {
    }

    public record Share(String baseUrl, String tokenSecret) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Cache(
            long fileListSeconds,
            long treeSeconds,
            long detailsSeconds) {
    }
}
