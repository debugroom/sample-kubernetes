package org.debugroom.sample.kubernetes.servicemesh.config;

import org.debugroom.sample.kubernetes.servicemesh.domain.ServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("local")
@Configuration
public class LocalConfig {

    @Autowired
    ServiceProperties serviceProperties;

    @Bean
    public WebClient service2WebClient() {
        return WebClient.builder()
                .baseUrl(serviceProperties.getService2().url)
                .build();

    }
}
