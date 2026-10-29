package com.tripledger.app.jobs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// turns on @Scheduled (only JobScheduler uses it, and only when tripledger.jobs.enabled is true)
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobSettings.class)
public class JobsConfig {
}
