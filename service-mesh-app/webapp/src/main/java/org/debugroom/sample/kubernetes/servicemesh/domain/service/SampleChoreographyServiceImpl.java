package org.debugroom.sample.kubernetes.servicemesh.domain.service;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SampleChoreographyServiceImpl implements SampleChoreographyService{

    @Autowired
    @Qualifier("service1Repository")
    ServiceRepository service1Repository;

    @Override
    public void execute(Sample sample) {
        service1Repository.save(sample);
    }

}
