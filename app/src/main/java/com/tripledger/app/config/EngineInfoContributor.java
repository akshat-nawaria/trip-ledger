package com.tripledger.app.config;

import com.tripledger.core.EngineInfo;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

// adds the rule-set version to GET /actuator/info, so you can see which rules a running server uses
@Component
public class EngineInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("ruleSetVersion", EngineInfo.RULE_SET_VERSION);
    }
}
