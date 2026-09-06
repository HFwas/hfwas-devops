package com.hfwas.devops.user.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.KeycloakAuthEvent;
import com.hfwas.devops.user.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/keycloak")
@RequiredArgsConstructor
public class KeycloakEventController {

    private final LoginLogService loginLogService;

    @Value("${keycloak.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/events")
    public BaseResult<Void> ingest(@RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
                                   @RequestBody KeycloakAuthEvent event) {
        if (StringUtils.isBlank(webhookSecret) || !webhookSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        loginLogService.ingest(event);
        return BaseResult.ok();
    }
}
