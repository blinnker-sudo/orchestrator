package com.ib.platform.orchestrator.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ib.platform.orchestrator.statemachine.OrchestratorEvents;
import com.ib.platform.orchestrator.statemachine.OrchestratorStates;

@RestController
public class HealthCheckController {

    private final KafkaAdmin kafkaAdmin;
    private final MongoTemplate mongoTemplate;
    private final StateMachineFactory<OrchestratorStates, OrchestratorEvents> stateMachineFactory;

    public HealthCheckController(KafkaAdmin kafkaAdmin,
                                 MongoTemplate mongoTemplate,
                                 StateMachineFactory<OrchestratorStates, OrchestratorEvents> stateMachineFactory) {
        this.kafkaAdmin = kafkaAdmin;
        this.mongoTemplate = mongoTemplate;
        this.stateMachineFactory = stateMachineFactory;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kafka", checkKafka());
        result.put("mongodb", checkMongo());
        result.put("statemachine", checkStateMachine());
        return result;
    }

    private Map<String, Object> checkKafka() {
        Map<String, Object> status = new LinkedHashMap<>();
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Set<String> topics = client.listTopics().names().get();
            status.put("status", "UP");
            status.put("topics", topics.stream().sorted().toList());
        } catch (InterruptedException | ExecutionException e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkMongo() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            String dbName = mongoTemplate.getDb().getName();
            status.put("status", "UP");
            status.put("database", dbName);
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkStateMachine() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            StateMachine<OrchestratorStates, OrchestratorEvents> sm = stateMachineFactory.getStateMachine();
            sm.startReactively().block();
            OrchestratorStates initial = sm.getState().getId();
            sm.sendEvent(reactor.core.publisher.Mono.just(
                    MessageBuilder.withPayload(OrchestratorEvents.INIT).build())).blockLast();
            OrchestratorStates afterInit = sm.getState().getId();
            sm.stopReactively().block();

            status.put("status", "UP");
            status.put("initialState", initial);
            status.put("afterInitEvent", afterInit);
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
}
