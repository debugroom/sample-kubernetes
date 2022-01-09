package org.debugroom.sample.kubernetes.servicemesh.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Component
@ConfigurationProperties(prefix = "service")
public class ServiceProperties {

    private Service1 service1;
    private Service2 service2;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Service1{
        public String dns;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Service2{
        public String dns;
    }

}
