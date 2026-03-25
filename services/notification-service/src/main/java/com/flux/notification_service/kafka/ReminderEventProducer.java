package com.flux.notification_service.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Produces reminder events to Kafka for async processing
 * or cross-service communication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderEventProducer {

    private static final String TOPIC = "reminder-events";

    private final KafkaTemplate<String, ReminderEvent> kafkaTemplate;

    public void publish(ReminderEvent event) {
        log.info("Publishing reminder event to Kafka: eventId={}, action={}",
                event.getEventId(), event.getAction());

        kafkaTemplate.send(TOPIC, event.getEventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish reminder event for eventId={}: {}",
                                event.getEventId(), ex.getMessage());
                    } else {
                        log.debug("Reminder event published: topic={}, partition={}, offset={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
