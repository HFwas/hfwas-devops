package cn.hfwas.devops.tools.api.sonarqube;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * @author houfei
 * @package cn.hfwas.devops.tools.api.sonarqube
 * @date 2025/1/3
 */
@FeignClient(name = "SonarqubeApi", url = "devops.sonarqube.url:10.10.103.229:33197")
public interface SonarqubeApi {

    @PostMapping("/api/webhooks/create")
    void webhook();

    @DeleteMapping("/api/webhooks/delete")
    void deleteWebhook();

    @GetMapping("/api/webhooks/list")
    void listWebhook();

    @PostMapping("/api/rules/create")
    void rules();

    @DeleteMapping("/api/rules/delete")
    void deleteRules();

    @GetMapping("/api/rules/search")
    void searchRules();

    @GetMapping("/api/rules/show")
    void showRules();

    @PostMapping("/api/rules/update")
    void updateRules();

    @PostMapping("/api/projects/create")
    void project();

    @PostMapping("/api/projects/delete")
    void deleteProjects();

    @GetMapping("/api/projects/search")
    List<String> projects();

}
