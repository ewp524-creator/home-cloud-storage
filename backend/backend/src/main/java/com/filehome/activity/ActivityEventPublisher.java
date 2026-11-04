package com.filehome.activity;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ActivityEventPublisher {

    public static final String TOPIC = "filehome.activity.v1";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ActivityEventPublisher.class);

    private final KafkaTemplate<String, ActivityEvent> kafkaTemplate;
    private final ActivityLogRepository fallbackRepository;

    public ActivityEventPublisher(
            KafkaTemplate<String, ActivityEvent> kafkaTemplate,
            ActivityLogRepository fallbackRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.fallbackRepository = fallbackRepository;
    }

    public void publishAfterCommit(UUID ownerId, String action, UUID entryId) {
        ActivityEvent event = new ActivityEvent(
                UUID.randomUUID(),
                ownerId,
                action,
                entryId,
                System.currentTimeMillis());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            send(event);
                        }
                    });
            return;
        }
        send(event);
    }

    private void send(ActivityEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.ownerId().toString(), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            LOGGER.warn(
                                    "Kafka audit publish failed; using database fallback",
                                    error);
                            fallback(event);
                        }
                    });
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Kafka audit publish failed synchronously; using database fallback",
                    exception);
            fallback(event);
        }
    }

    private void fallback(ActivityEvent event) {
        try {
            fallbackRepository.append(event);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not persist fallback audit event", exception);
        }
    }
}
