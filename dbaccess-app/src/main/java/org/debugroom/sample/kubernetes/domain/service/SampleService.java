package org.debugroom.sample.kubernetes.domain.service;

import org.debugroom.sample.kubernetes.domain.model.entity.User;

import java.util.List;

public interface SampleService {

    public List<User> getUsers();
    public User getUser(Long id);
    public User getUserByLoginId(String loginId);

}
