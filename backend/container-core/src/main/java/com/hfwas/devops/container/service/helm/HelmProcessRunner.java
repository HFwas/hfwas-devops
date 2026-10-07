package com.hfwas.devops.container.service.helm;

public interface HelmProcessRunner {

    HelmProcessResult run(HelmProcessCommand command);
}
