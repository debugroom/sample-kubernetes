package org.debugroom.sample.kubernetes.servicemesh.app.web;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.SampleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service1")
public class SampleController {

    @Autowired
    SampleRepository sampleRepository;

    @GetMapping("/sample")
    public Sample getSamples(){
        return sampleRepository.fineOne();
    }

}
