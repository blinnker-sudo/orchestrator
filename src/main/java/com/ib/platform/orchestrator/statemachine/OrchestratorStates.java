package com.ib.platform.orchestrator.statemachine;

public enum OrchestratorStates {
    IDLE,
    INITIALIZED,
    STARTED,
    SUSPENDED,
    DELIVERING,
    DELIVERED,
    FAILED
}
