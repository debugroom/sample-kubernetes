package org.debugroom.sample.kubernetes.servicemesh.app.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.SampleAsyncRepository;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.SampleSyncRepository;
import java.text.SimpleDateFormat;

@RestController
@RequestMapping("/service1")
public class SampleController {

    @Autowired
    SampleSyncRepository sampleSyncRepository;

    @Autowired
    SampleAsyncRepository sampleAsyncRepository;

    @GetMapping("/sample")
    public Sample getSample() {
        return sampleSyncRepository.findOne();
    }

    @PostMapping("/sample")
    public Sample saveSample(String message){
        return sampleAsyncRepository.save(Sample.builder()
                .text("test-message: " + message + ":"
                        + (new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-z"))
                        .getCalendar().getTime().toString())
                .build());
    }

    @GetMapping("/test")
    public Sample test() {
        return Sample.builder()
                .text("This is created by Service1")
                .build();
    }

}
