package com.hfwas.devops.pm.spi.plugin;

import org.springframework.stereotype.Component;

@Component
public class RequirementTypePlugin extends AbstractWorkItemTypePlugin {
    @Override
    public String typeCode() {
        return "requirement";
    }
}
