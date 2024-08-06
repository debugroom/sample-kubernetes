package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

@Component
public class SampleWebClientRepositoryImpl implements SampleSyncRepository{

    @Autowired
    WebClient webClient;

    @Override
    public Sample findOne() {
        String endpoint = "/service2/sample";
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(endpoint).build())
                .retrieve()
                .bodyToMono(Sample.class)
                .block();
    }

}
