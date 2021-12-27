package org.debugroom.sample.kubernetes.servicemesh.app.web;

import org.debugroom.sample.kubernetes.servicemesh.app.model.Sample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service2")
public class SampleController {

    @GetMapping("/sample")
    public Sample getSample(){
        return Sample.builder()
                .text("This is created by Service2.")
                .build();
    }

}
