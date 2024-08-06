package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

public interface SampleAsyncRepository {

    Sample save(Sample sample);

}
