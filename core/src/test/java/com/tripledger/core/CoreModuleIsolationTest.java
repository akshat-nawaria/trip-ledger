package com.tripledger.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The case study wants the core logic testable without a web server or a DB.
 * This test is a tripwire: if someone pulls Spring or a JDBC driver into core, it goes red.
 */
class CoreModuleIsolationTest {

    @Test
    void springIsNotOnTheCoreClasspath() {
        assertThatThrownBy(() -> Class.forName("org.springframework.context.ApplicationContext"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void noDatabaseDriverIsOnTheCoreClasspath() {
        assertThatThrownBy(() -> Class.forName("org.postgresql.Driver"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void ruleSetVersionIsDefined() {
        assertThat(EngineInfo.RULE_SET_VERSION).isEqualTo("v1");
    }
}
