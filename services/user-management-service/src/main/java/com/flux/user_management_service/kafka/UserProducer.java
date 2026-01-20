package com.flux.user_management_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProducer {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private static final String TOPIC = "user-topic";

    public void sendUserEvent(UserEvent event) {
        log.info("Sending user event to Kafka: {}", event);
        kafkaTemplate.send(TOPIC, event.getUserId(), event);
    }
}
