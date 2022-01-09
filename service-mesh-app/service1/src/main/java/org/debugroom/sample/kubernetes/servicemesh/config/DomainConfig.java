package org.debugroom.sample.kubernetes.servicemesh.config;

import org.debugroom.sample.kubernetes.servicemesh.domain.ServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@ComponentScan("org.debugroom.sample.kubernetes.servicemesh.domain")
@Configuration
public class DomainConfig {

    @Autowired
    ServiceProperties serviceProperties;

    @Bean
    public WebClient service2WebClient() {
        return WebClient.builder()
                .baseUrl(serviceProperties.getService2().dns)
                .build();
    }

}
