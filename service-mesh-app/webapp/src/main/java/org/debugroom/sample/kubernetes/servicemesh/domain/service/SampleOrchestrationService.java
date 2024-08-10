package org.debugroom.sample.kubernetes.servicemesh.domain.service;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

public interface SampleOrchestrationService {

    public Sample execute(Sample sample);

}
