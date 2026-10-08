package com.hfwas.devops.container.service.helm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "helm.release")
public class HelmReleaseProperties {

    /** Install, upgrade, rollback, and uninstall without {@code --wait}. */
    private int timeoutSeconds = 180;
    /** Used when the caller sets wait or rollback-on-failure ({@code --atomic}). */
    private int waitTimeoutSeconds = 600;
    /** list, status, values, history, and registry login. */
    private int queryTimeoutSeconds = 60;

    public int timeoutSeconds() {
        return timeoutSeconds > 0 ? timeoutSeconds : 180;
    }

    public int waitTimeoutSeconds() {
        return waitTimeoutSeconds > 0 ? waitTimeoutSeconds : 600;
    }

    public int queryTimeoutSeconds() {
        return queryTimeoutSeconds > 0 ? queryTimeoutSeconds : 60;
    }
}
