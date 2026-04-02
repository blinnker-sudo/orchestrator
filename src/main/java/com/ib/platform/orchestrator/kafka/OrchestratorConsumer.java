package com.ib.platform.orchestrator.kafka;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Service
public class OrchestratorConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorConsumer.class);

    @KafkaListener(topics = "dev-ib-platform-init-orchestrator-request-country",
                   groupId = "ib-orchestrator-group")
    public void onInitRequest(ConsumerRecord<String, Map<String, Object>> record) {
        log.info("Received INIT request: key={}, value={}", record.key(), record.value());
    }

    @KafkaListener(topics = "dev-ib-platform-start-orchestrator-request-country",
                   groupId = "ib-orchestrator-group")
    public void onStartRequest(ConsumerRecord<String, Map<String, Object>> record) {
        log.info("Received START request: key={}, value={}", record.key(), record.value());
    }

    @KafkaListener(topics = "dev-ib-platform-resume-orchestrator-request-country",
                   groupId = "ib-orchestrator-group")
    public void onResumeRequest(ConsumerRecord<String, Map<String, Object>> record) {
        log.info("Received RESUME request: key={}, value={}", record.key(), record.value());
    }

    @KafkaListener(topics = "dev-ib-platform-delivery-orchestrator-request-country",
                   groupId = "ib-orchestrator-group")
    public void onDeliveryRequest(ConsumerRecord<String, Map<String, Object>> record) {
        log.info("Received DELIVERY request: key={}, value={}", record.key(), record.value());
    }
}
