package org.debugroom.sample.spring.cloud.kubernetes.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@EnableDiscoveryClient
public class DiscoveryClientConfig implements InitializingBean {

    @Autowired
    DiscoveryClient discoveryClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        // Sample of Getting ServiceInstnaces by using Discovery Client.
        List<String> services = discoveryClient.getServices();
        String description = discoveryClient.description();
        List<ServiceInstance> serviceInstances =
                discoveryClient.getInstances("istio-ingressgateway");
        // Use Kubernetes ApiClient.
        ApiClient apiClient = ClientBuilder.standard().build();
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        V1ServiceList v1ServiceList = coreV1Api.listServiceForAllNamespaces(
                null,null,null,null,null, null, null, null, null, null);
        for(V1Service v1Service : v1ServiceList.getItems()){
           String kind = v1Service.getKind();
            V1ObjectMeta v1ObjectMeta = v1Service.getMetadata();
           String metadataName = v1ObjectMeta.getName();
           if("istio-ingressgateway".equals(metadataName)){
               V1LoadBalancerIngress loadBalancerIngress = v1Service.getStatus().getLoadBalancer().getIngress().get(0);
               String hostname = loadBalancerIngress.getHostname();
           }
        }

        serviceInstances.stream()
                .forEach(serviceInstance -> log.info(serviceInstance.toString()));

    }

}
