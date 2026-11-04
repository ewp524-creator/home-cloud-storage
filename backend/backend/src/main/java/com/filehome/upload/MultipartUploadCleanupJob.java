package com.filehome.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MultipartUploadCleanupJob {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MultipartUploadCleanupJob.class);

    private final MultipartUploadService uploadService;

    public MultipartUploadCleanupJob(MultipartUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 900_000)
    public void cleanupExpiredSessions() {
        int removed = uploadService.cleanupExpired();
        if (removed > 0) {
            LOGGER.info("Cleaned {} expired multipart uploads", removed);
        }
    }
}
