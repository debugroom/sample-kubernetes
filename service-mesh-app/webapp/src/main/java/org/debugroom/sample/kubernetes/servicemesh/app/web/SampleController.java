package org.debugroom.sample.kubernetes.servicemesh.app.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.debugroom.sample.kubernetes.servicemesh.domain.repository.ServiceRepository;

@Controller
public class SampleController {

    @Autowired
    OAuth2AuthorizedClientService auth2AuthorizedClientService;

    @Autowired
    @Qualifier("service1Repository")
    ServiceRepository service1Repository;

    @Autowired
    @Qualifier("service2Repository")
    ServiceRepository service2Repository;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal OidcUser oidcUser,
                        OAuth2AuthenticationToken authenticationToken, Model model){
        return portal(oidcUser, authenticationToken, model);
    }

    @GetMapping(value = "/portal")
    public String portal(@AuthenticationPrincipal OidcUser oidcUser,
                         OAuth2AuthenticationToken oAuth2AuthenticationToken,
                         Model model){
        OAuth2AuthorizedClient oAuth2AuthorizedClient =
                auth2AuthorizedClientService.loadAuthorizedClient(
                        oAuth2AuthenticationToken.getAuthorizedClientRegistrationId(),
                        oAuth2AuthenticationToken.getName());
        model.addAttribute("oidcUser", oidcUser);
        model.addAttribute(oAuth2AuthorizedClient);
        model.addAttribute("accessToken", oAuth2AuthorizedClient.getAccessToken());
        model.addAttribute("sample1", service1Repository.findTest());
        model.addAttribute("sample2viaSample1", service1Repository.findOne());
        model.addAttribute("sample2", service2Repository.findOne());
        return "portal";

    }

}
