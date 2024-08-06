package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;

public interface SampleSyncRepository {

    Sample findOne();

}
