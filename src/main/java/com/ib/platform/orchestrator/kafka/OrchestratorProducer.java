package com.ib.platform.orchestrator.kafka;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorProducer {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrchestratorProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendResponse(String topic, String key, Map<String, Object> payload) {
        log.info("Sending message to topic={}, key={}", topic, key);
        kafkaTemplate.send(topic, key, payload);
    }

    public void sendTracingEvent(String key, Map<String, Object> event) {
        kafkaTemplate.send("dev-ib-platform-event-tracing-country", key, event);
    }
}
