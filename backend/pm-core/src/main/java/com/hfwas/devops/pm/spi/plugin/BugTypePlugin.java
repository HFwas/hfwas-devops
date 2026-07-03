package com.hfwas.devops.pm.spi.plugin;

import org.springframework.stereotype.Component;

@Component
public class BugTypePlugin extends AbstractWorkItemTypePlugin {
    @Override
    public String typeCode() {
        return "bug";
    }
}
