package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

public interface ServiceRepository {

    public Sample findOne();

    public Sample findTest();

    public Sample save(Sample sample);

}
