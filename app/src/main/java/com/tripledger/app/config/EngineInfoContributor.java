package com.tripledger.app.config;

import com.tripledger.core.EngineInfo;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/** Shows which rule-set version this running service uses, at {@code GET /actuator/info}. */
@Component
public class EngineInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("ruleSetVersion", EngineInfo.RULE_SET_VERSION);
    }
}
