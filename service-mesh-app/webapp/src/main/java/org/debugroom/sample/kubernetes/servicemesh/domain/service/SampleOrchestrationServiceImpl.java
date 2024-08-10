package org.debugroom.sample.kubernetes.servicemesh.domain.service;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SampleOrchestrationServiceImpl implements SampleOrchestrationService{

    @Autowired
    @Qualifier("service1Repository")
    ServiceRepository service1Repository;

    @Autowired
    @Qualifier("service2Repository")
    ServiceRepository service2Repository;

    @Override
    public Sample execute(Sample sample) {

        if("sample1".equals(sample.getText())){
            return service1Repository.findTest();
        }
        if("sample2viaSample1".equals(sample.getText())){
            return service1Repository.findOne();
        }
        return service2Repository.findOne();

    }
}
