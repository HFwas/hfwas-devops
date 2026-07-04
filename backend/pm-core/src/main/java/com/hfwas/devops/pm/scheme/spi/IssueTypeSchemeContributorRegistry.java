package com.hfwas.devops.pm.scheme.spi;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class IssueTypeSchemeContributorRegistry {

    private final List<IssueTypeSchemeContributor> contributors;

    public IssueTypeSchemeContributorRegistry(List<IssueTypeSchemeContributor> contributors) {
        this.contributors = contributors.stream()
                .sorted(Comparator.comparingInt(IssueTypeSchemeContributor::order))
                .toList();
    }

    public List<IssueTypeSchemeContributor> all() {
        return contributors;
    }
}
