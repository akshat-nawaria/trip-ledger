package com.tripledger.core;

/**
 * Which version of the rules this engine runs.
 *
 * Gets saved with every result. Same input + same version should always give
 * exactly the same km - that's the promise we make to vendors.
 */
public final class EngineInfo {

    // bump this whenever a rule (or the order of the rules) changes, so old bills keep their old version
    //   v1  the rules from the case study
    //   v2  a move smaller than GPS accuracy is never judged as speeding (S9 benchmark finding).
    //       Changes labels only, never km: a rejected piece and a short gap both pay the straight line
    public static final String RULE_SET_VERSION = "v2";

    private EngineInfo() {
        // just constants, no instances
    }
}
