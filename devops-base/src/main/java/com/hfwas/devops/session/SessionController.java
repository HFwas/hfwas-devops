package com.hfwas.devops.session;

import com.hfwas.devops.common.core.base.BaseResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

/**
 * @author houfei
 * @package com.hfwas.devops.session
 * @date 2025/3/19
 */
@RestController
@RequestMapping("/oauth")
public class SessionController {

    @GetMapping("/")
    public BaseResult index(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                            @AuthenticationPrincipal OAuth2User oauth2User) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", oauth2User.getName());
        map.put("login", oauth2User.getAttribute("login"));
        map.put("id", oauth2User.getAttribute("id"));
        map.put("location", oauth2User.getAttribute("location"));
        map.put("bio", oauth2User.getAttribute("bio"));
        return BaseResult.ok(map);
    }

    @GetMapping("/user")
    public String getUser(@AuthenticationPrincipal OAuth2User principal) {
        return "Hello, " + principal.getAttribute("nickname");
    }

    @GetMapping("/gitee")
    public BaseResult gitee(@RequestParam("code") String code) {
        System.out.println("gitee code:" + code);

        return BaseResult.ok();
    }

}
