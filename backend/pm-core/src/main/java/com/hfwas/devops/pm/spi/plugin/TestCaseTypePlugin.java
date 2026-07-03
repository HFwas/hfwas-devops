package com.hfwas.devops.pm.spi.plugin;

import org.springframework.stereotype.Component;

@Component
public class TestCaseTypePlugin extends AbstractWorkItemTypePlugin {
    @Override
    public String typeCode() {
        return "test_case";
    }
}
