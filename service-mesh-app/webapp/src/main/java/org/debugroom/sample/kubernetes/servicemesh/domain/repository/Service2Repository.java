package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

@Component
public class Service2Repository implements ServiceRepository{

    @Autowired
    @Qualifier("service2WebClient")
    WebClient webClient;

    @Override
    public Sample findOne(){
        return webClient.get()
                .uri("/service2/sample")
                .retrieve()
                .bodyToMono(Sample.class)
                .block();
    }

    @Override
    public Sample findTest(){
        return Sample.builder().text("test").build();
    }

    @Override
    public Sample save(Sample sample) {
        return Sample.builder().text("test").build();
    }

}
