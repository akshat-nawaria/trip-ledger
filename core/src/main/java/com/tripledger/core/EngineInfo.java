package com.tripledger.core;

/**
 * Identity of the distance engine.
 *
 * <p>The rule-set version is stored with every computed result. Two runs with the same input and
 * the same rule-set version must always produce exactly the same kilometres.
 */
public final class EngineInfo {

    /** Version of the ordered segment rules. Change it whenever a rule or its order changes. */
    public static final String RULE_SET_VERSION = "v1";

    private EngineInfo() {
        // constants only
    }
}
