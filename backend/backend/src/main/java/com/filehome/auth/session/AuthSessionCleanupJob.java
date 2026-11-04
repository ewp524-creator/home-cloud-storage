package com.filehome.auth.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionCleanupJob {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuthSessionCleanupJob.class);

    private final AuthSessionService sessionService;

    public AuthSessionCleanupJob(AuthSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void removeExpiredSessions() {
        int removed = sessionService.deleteExpired();
        if (removed > 0) {
            LOGGER.info("Removed {} expired authentication sessions", removed);
        }
    }
}
