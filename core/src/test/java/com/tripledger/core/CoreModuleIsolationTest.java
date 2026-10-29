package com.tripledger.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Guards instruction 4 of the case study: the core algorithm must be testable without a web
 * server or a database. If anyone adds Spring or a JDBC driver to the core module, this fails.
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
