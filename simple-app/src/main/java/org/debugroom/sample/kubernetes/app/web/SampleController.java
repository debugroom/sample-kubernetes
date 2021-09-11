package org.debugroom.sample.kubernetes.app.web;

import org.debugroom.sample.kubernetes.app.model.Sample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleController {

    @GetMapping("/portal")
    public Sample portal(){
        return Sample.builder()
                .text("Hello!Kubernetes!")
                .build();
    }

}
