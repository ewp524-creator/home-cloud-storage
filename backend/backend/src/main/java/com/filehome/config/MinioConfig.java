package com.filehome.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.minio.MinioClient;

@Configuration
public class MinioConfig {

    @Bean
    MinioClient minioClient(AppProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.storage().endpoint())
                .credentials(
                        properties.storage().accessKey(),
                        properties.storage().secretKey())
                .build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
