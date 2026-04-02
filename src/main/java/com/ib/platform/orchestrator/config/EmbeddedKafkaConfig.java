package com.ib.platform.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.test.EmbeddedKafkaZKBroker;

/**
 * Replaces Zookeeper + Kafka containers from docker-compose.
 * Starts an embedded Kafka broker with ZooKeeper on fixed ports.
 */
@Configuration
@Profile("local-embedded")
public class EmbeddedKafkaConfig {

    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    public EmbeddedKafkaZKBroker embeddedKafkaBroker() {
        EmbeddedKafkaZKBroker broker = new EmbeddedKafkaZKBroker(1, false, 1);
        broker.kafkaPorts(9092);
        broker.zkPort(2181);
        return broker;
    }
}
