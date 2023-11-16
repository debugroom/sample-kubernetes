package org.debugroom.sample.kubernetes.servicemesh.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.debugroom.sample.kubernetes.servicemesh.domain.model.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByUserId(Long userId);

}
