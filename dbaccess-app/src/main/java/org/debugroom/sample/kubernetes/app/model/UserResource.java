package org.debugroom.sample.kubernetes.app.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UserResource {

    private String userId;
    private String firstName;
    private String familyName;
    private String loginId;
    private boolean isLogin;
    private boolean isAdmin;
    private String imageFilePath;
    private List<CredentialResource> credentialResources;

}
