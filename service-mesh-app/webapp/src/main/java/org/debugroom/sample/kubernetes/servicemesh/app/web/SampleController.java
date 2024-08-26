package org.debugroom.sample.kubernetes.servicemesh.app.web;

import lombok.extern.slf4j.Slf4j;
import org.debugroom.sample.kubernetes.servicemesh.domain.model.Sample;
import org.debugroom.sample.kubernetes.servicemesh.domain.repository.Service1Repository;
import org.debugroom.sample.kubernetes.servicemesh.domain.service.SampleChoreographyService;
import org.debugroom.sample.kubernetes.servicemesh.domain.service.SampleOrchestrationService;
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

@Slf4j
@Controller
public class SampleController {


    @Autowired
    OAuth2AuthorizedClientService auth2AuthorizedClientService;

    @Autowired
    SampleOrchestrationService sampleOrchestrationService;

    @Autowired
    SampleChoreographyService sampleChoreographyService;

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
        log.info(this.getClass().getName() + ": AccessToken : " + oAuth2AuthorizedClient.getAccessToken().getTokenValue());
        model.addAttribute("oidcUser", oidcUser);
        model.addAttribute(oAuth2AuthorizedClient);
        model.addAttribute("accessToken", oAuth2AuthorizedClient.getAccessToken());
        model.addAttribute("sample1",
                sampleOrchestrationService.execute(Sample.builder().text("sample1").build()));
        model.addAttribute("sample2viaSample1",
                sampleOrchestrationService.execute(Sample.builder().text("sample2viaSample1").build()));
        model.addAttribute("sample2",
                sampleOrchestrationService.execute(Sample.builder().text("sample2").build()));
        sampleChoreographyService.execute(Sample.builder().text("messageFromWebApp").build());

        return "portal";

    }

}
