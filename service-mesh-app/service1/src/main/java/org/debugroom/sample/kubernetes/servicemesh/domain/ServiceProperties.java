package org.debugroom.sample.kubernetes.servicemesh.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Component
@ConfigurationProperties(prefix = "service")
public class ServiceProperties {

    private Service2 service2;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Service2 {
        public String url;
    }

}

