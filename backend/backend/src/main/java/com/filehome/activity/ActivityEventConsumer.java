package com.filehome.activity;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ActivityEventConsumer {

    private final ActivityLogRepository repository;

    public ActivityEventConsumer(ActivityLogRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = ActivityEventPublisher.TOPIC,
            groupId = "filehome-activity-writer",
            concurrency = "3")
    public void consume(ActivityEvent event) {
        repository.append(event);
    }
}
