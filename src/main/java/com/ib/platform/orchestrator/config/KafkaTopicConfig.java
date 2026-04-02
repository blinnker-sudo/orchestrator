package com.ib.platform.orchestrator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Replaces init-kafka container from docker-compose.
 * Creates all required topics via KafkaAdmin auto-configuration.
 */
@Configuration
@Profile("local-embedded")
public class KafkaTopicConfig {

    // ── Request topics (4 partitions) ──

    @Bean
    public NewTopic startOrchestratorRequestTopic() {
        return TopicBuilder.name("dev-ib-platform-start-orchestrator-request-country")
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic resumeOrchestratorRequestTopic() {
        return TopicBuilder.name("dev-ib-platform-resume-orchestrator-request-country")
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deliveryOrchestratorRequestTopic() {
        return TopicBuilder.name("dev-ib-platform-delivery-orchestrator-request-country")
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic initOrchestratorRequestTopic() {
        return TopicBuilder.name("dev-ib-platform-init-orchestrator-request-country")
                .partitions(4)
                .replicas(1)
                .build();
    }

    // ── Response topics (start/resume: 2 partitions, delivery/init: 3 partitions) ──

    @Bean
    public NewTopic startOrchestratorResponseTopic() {
        return TopicBuilder.name("dev-ib-platform-start-orchestrator-response-country")
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic resumeOrchestratorResponseTopic() {
        return TopicBuilder.name("dev-ib-platform-resume-orchestrator-response-country")
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deliveryOrchestratorResponseTopic() {
        return TopicBuilder.name("dev-ib-platform-delivery-orchestrator-response-country")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic initOrchestratorResponseTopic() {
        return TopicBuilder.name("dev-ib-platform-init-orchestrator-response-country")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Event tracing topic (5 partitions) ──

    @Bean
    public NewTopic eventTracingTopic() {
        return TopicBuilder.name("dev-ib-platform-event-tracing-country")
                .partitions(5)
                .replicas(1)
                .build();
    }
}
