package org.debugroom.sample.kubernetes.servicemesh.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan("org.debugroom.sample.kubernetes.servicemesh.domain")
@Configuration
public class DomainConfig {
}
