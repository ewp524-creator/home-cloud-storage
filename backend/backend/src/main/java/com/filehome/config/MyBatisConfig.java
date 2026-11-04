package com.filehome.config;

import java.time.Instant;
import java.util.UUID;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.filehome.persistence.InstantTypeHandler;
import com.filehome.persistence.UuidTypeHandler;

@Configuration
public class MyBatisConfig {

    @Bean
    ConfigurationCustomizer fileHomeTypeHandlers() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(
                    UUID.class,
                    new UuidTypeHandler());
            configuration.getTypeHandlerRegistry().register(
                    Instant.class,
                    new InstantTypeHandler());
        };
    }
}
