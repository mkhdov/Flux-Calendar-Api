package com.flux.user_management_service.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic userTopic() {
        return TopicBuilder.name("user-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
