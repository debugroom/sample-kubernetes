package org.debugroom.sample.kubernetes.servicemesh.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.debugroom.sample.kubernetes.servicemesh.domain")
public class DomainConfig {
}
