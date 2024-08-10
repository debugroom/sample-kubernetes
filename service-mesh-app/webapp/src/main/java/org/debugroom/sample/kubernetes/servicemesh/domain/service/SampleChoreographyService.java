package org.debugroom.sample.kubernetes.servicemesh.domain.service;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

public interface SampleChoreographyService {

    public void execute(Sample sample);

}
