package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class Service1Repository implements ServiceRepository{

    @Autowired
    @Qualifier("service1WebClient")
    WebClient webClient;

    public Sample findOne(){
        return webClient.get()
                .uri("/service1/sample")
                .retrieve()
                .bodyToMono(Sample.class)
                .block();
    }

    public Sample findTest(){
        return webClient.get()
                .uri("/service1/test")
                .retrieve()
                .bodyToMono(Sample.class)
                .block();
    }

    @Override
    public Sample save(Sample sample) {
        return webClient.post()
                .uri("/service1/sample?message=" + sample.getText())
//                .bodyValue(sample.getText())
                .retrieve()
                .bodyToMono(Sample.class)
                .block();
    }

}
