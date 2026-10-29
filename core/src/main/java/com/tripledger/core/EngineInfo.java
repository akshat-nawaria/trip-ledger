package com.tripledger.core;

/**
 * Which version of the rules this engine runs.
 *
 * Gets saved with every result. Same input + same version should always give
 * exactly the same km - that's the promise we make to vendors.
 */
public final class EngineInfo {

    // bump this whenever a rule (or the order of the rules) changes, so old bills keep their old version
    public static final String RULE_SET_VERSION = "v1";

    private EngineInfo() {
        // just constants, no instances
    }
}
