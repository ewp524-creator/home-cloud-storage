package com.filehome.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.filehome.activity.ActivityEventPublisher;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic activityTopic() {
        return TopicBuilder.name(ActivityEventPublisher.TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
