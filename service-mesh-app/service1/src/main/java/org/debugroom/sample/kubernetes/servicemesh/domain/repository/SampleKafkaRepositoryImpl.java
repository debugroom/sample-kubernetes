package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

@Component
public class SampleKafkaRepositoryImpl implements SampleAsyncRepository{

    @Autowired
    private StreamBridge streamBridge;

    @Override
    public Sample save(Sample sample) {
        streamBridge.send("sample-topic", sample);
        return sample;
    }

}
