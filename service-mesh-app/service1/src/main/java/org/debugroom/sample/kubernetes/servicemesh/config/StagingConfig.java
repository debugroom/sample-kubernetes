package org.debugroom.sample.kubernetes.servicemesh.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Profile("staging")
@Configuration
public class StagingConfig {

    @Bean
    public WebClient service2WebClient() throws Exception{

        ApiClient apiClient = ClientBuilder.standard().build();
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        List<V1Service> serviceList =
            coreV1Api.listServiceForAllNamespaces(
                    null, null,null, null, null, null, null, null, null, null
            ).getItems();
        log.info("These are servicelist --------------------");
        log.info(serviceList.toString());
        return WebClient.builder()
                .baseUrl("http://" + serviceList.stream().filter(
                        v1Service -> "istio-ingressgateway".equals(v1Service.getMetadata().getName())
                ).findFirst().get().getSpec().getClusterIP())
                .build();
        // LoadBalancerのホスト名だとEgressおよびServiceEntryの設定が必要になる。
//        try{
//        V1LoadBalancerIngress loadBalancerIngress =serviceList.stream().filter(
//                    v1Service ->
//                            "istio-ingressgateway".equals(v1Service.getMetadata().getName())
//            ).findFirst().get().getStatus().getLoadBalancer().getIngress().get(0);
//        log.info("Istio Ingress gateway settings are : " + loadBalancerIngress.getHostname());
//        return WebClient.builder()
//                .baseUrl("http://" + loadBalancerIngress.getHostname())
//                .build();
//        } catch (ApiException e){
//            log.error(e.getResponseBody());
//            e.printStackTrace();
//        }
//        return null;
    }

}
