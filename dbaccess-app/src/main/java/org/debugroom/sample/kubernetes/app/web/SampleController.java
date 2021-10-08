package org.debugroom.sample.kubernetes.app.web;

import io.swagger.annotations.ApiOperation;
import org.debugroom.sample.kubernetes.app.model.UserResource;
import org.debugroom.sample.kubernetes.app.model.UserResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.debugroom.sample.kubernetes.app.model.Sample;
import org.debugroom.sample.kubernetes.domain.service.SampleService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/sample-api")
public class SampleController {

    @Autowired
    SampleService sampleService;

    @GetMapping("/samples/{id}")
    @ApiOperation("Get Sample resources by id.")
    public Sample getSample(@PathVariable String id){
        return Sample.builder()
                .text(id)
                .build();
    }

    @GetMapping("/samples")
    public List<Sample> getSamples(){
        return Arrays.asList(Sample.builder().text("0").build());
    }

    @GetMapping("/users")
    public List<UserResource> getUsers(){
        return UserResourceMapper.mapWithCredential(
                sampleService.getUsers());
    }

    @GetMapping("/users/{id:[0-9]+}")
    public UserResource getUser(@PathVariable Long id){
        return UserResourceMapper.mapWithCredential(
                sampleService.getUser(id));
    }

    @GetMapping("/users/user")
    public UserResource getUserByLoginId(@RequestParam String loginId){
        return UserResourceMapper.mapWithCredential(
                sampleService.getUserByLoginId(loginId));
    }

}
