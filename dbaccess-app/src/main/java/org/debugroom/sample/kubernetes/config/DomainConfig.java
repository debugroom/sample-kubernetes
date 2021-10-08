package org.debugroom.sample.kubernetes.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan("org.debugroom.sample.kubernetes.domain.service")
@Configuration
public class DomainConfig {
}
