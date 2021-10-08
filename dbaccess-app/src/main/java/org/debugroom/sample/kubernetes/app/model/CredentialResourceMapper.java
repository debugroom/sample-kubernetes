package org.debugroom.sample.kubernetes.app.model;

import org.debugroom.sample.kubernetes.domain.model.entity.Credential;

import java.util.List;
import java.util.stream.Collectors;

public interface CredentialResourceMapper {

    public static CredentialResource map(Credential credential){
        return CredentialResource.builder()
                .userId(credential.getUserId())
                .credentialType(credential.getCredentialType())
                .credentialKey(credential.getCredentialKey())
                .validDate(credential.getValidDate())
                .build();
    }

    public static List<CredentialResource> map(List<Credential> credentials){
        return credentials.stream().map(CredentialResourceMapper::map)
                .collect(Collectors.toList());
    }

}
