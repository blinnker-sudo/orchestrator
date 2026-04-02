package com.ib.platform.orchestrator.statemachine;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

/**
 * Spring Statemachine configuration for the orchestrator.
 *
 * Flow: IDLE -> INITIALIZED -> STARTED <-> SUSPENDED -> DELIVERING -> DELIVERED
 * Any state can transition to FAILED on FAIL event.
 */
@Configuration
@Profile("local-embedded")
@EnableStateMachineFactory
public class StateMachineConfig
        extends EnumStateMachineConfigurerAdapter<OrchestratorStates, OrchestratorEvents> {

    private static final Logger log = LoggerFactory.getLogger(StateMachineConfig.class);

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrchestratorStates, OrchestratorEvents> config)
            throws Exception {
        config
                .withConfiguration()
                .autoStartup(false)
                .listener(new StateMachineListenerAdapter<>() {
                    @Override
                    public void stateChanged(State<OrchestratorStates, OrchestratorEvents> from,
                                             State<OrchestratorStates, OrchestratorEvents> to) {
                        log.info("State changed from {} to {}",
                                from != null ? from.getId() : "none",
                                to != null ? to.getId() : "none");
                    }
                });
    }

    @Override
    public void configure(StateMachineStateConfigurer<OrchestratorStates, OrchestratorEvents> states)
            throws Exception {
        states
                .withStates()
                .initial(OrchestratorStates.IDLE)
                .end(OrchestratorStates.DELIVERED)
                .end(OrchestratorStates.FAILED)
                .states(EnumSet.allOf(OrchestratorStates.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrchestratorStates, OrchestratorEvents> transitions)
            throws Exception {
        transitions
                // Happy path
                .withExternal()
                    .source(OrchestratorStates.IDLE).target(OrchestratorStates.INITIALIZED)
                    .event(OrchestratorEvents.INIT)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.INITIALIZED).target(OrchestratorStates.STARTED)
                    .event(OrchestratorEvents.START)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.STARTED).target(OrchestratorStates.SUSPENDED)
                    .event(OrchestratorEvents.SUSPEND)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.SUSPENDED).target(OrchestratorStates.STARTED)
                    .event(OrchestratorEvents.RESUME)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.STARTED).target(OrchestratorStates.DELIVERING)
                    .event(OrchestratorEvents.DELIVER)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.DELIVERING).target(OrchestratorStates.DELIVERED)
                    .event(OrchestratorEvents.COMPLETE)
                    .and()
                // Error transitions from any non-terminal state
                .withExternal()
                    .source(OrchestratorStates.IDLE).target(OrchestratorStates.FAILED)
                    .event(OrchestratorEvents.FAIL)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.INITIALIZED).target(OrchestratorStates.FAILED)
                    .event(OrchestratorEvents.FAIL)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.STARTED).target(OrchestratorStates.FAILED)
                    .event(OrchestratorEvents.FAIL)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.SUSPENDED).target(OrchestratorStates.FAILED)
                    .event(OrchestratorEvents.FAIL)
                    .and()
                .withExternal()
                    .source(OrchestratorStates.DELIVERING).target(OrchestratorStates.FAILED)
                    .event(OrchestratorEvents.FAIL);
    }
}
