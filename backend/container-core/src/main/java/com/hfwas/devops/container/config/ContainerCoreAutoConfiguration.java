package com.hfwas.devops.container.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration entry for container-core module.
 * Module beans are discovered via {@code com.hfwas.devops} package scanning
 * from the main application; this class exists to document the config root
 * and enable scheduling for heartbeat jobs.
 */
@Configuration
@EnableScheduling
public class ContainerCoreAutoConfiguration {
}