package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SampleRepositoryImpl implements SampleRepository{

    @Autowired
    WebClient webClient;

    @Override
    public Sample fineOne() {
        String endpoint = "/service2/sample";
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(endpoint).build())
                .retrieve()
                .bodyToMono(Sample.class)
                .block();
    }

}
