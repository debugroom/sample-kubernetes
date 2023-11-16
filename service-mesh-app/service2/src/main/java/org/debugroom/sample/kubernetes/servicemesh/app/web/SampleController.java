package org.debugroom.sample.kubernetes.servicemesh.app.web;

import org.debugroom.sample.kubernetes.servicemesh.app.model.Sample;
import org.debugroom.sample.kubernetes.servicemesh.domain.model.entity.User;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service2")
public class SampleController {

    @Autowired
    UserRepository userRepository;

    @GetMapping("/sample")
    public Sample getSample() {
        return Sample.builder()
                .text("This is created by Service2.")
                .build();
    }

    @GetMapping("/users/{userId:[0-9]+}")
    public User getUser(@PathVariable Long userId){
        return userRepository.findByUserId(userId);
    }

}
