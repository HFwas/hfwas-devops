package com.hfwas.devops.apitest.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Skips DB integration tests when neither an external MySQL URL nor Docker is available.
 * Registered on the base test class so the condition is inherited by all subclasses.
 */
public class MySqlTestCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String externalUrl = System.getProperty("test.mysql.url");
        if (externalUrl != null && !externalUrl.isBlank()) {
            return ConditionEvaluationResult.enabled("external MySQL configured");
        }
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker available");
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return ConditionEvaluationResult.disabled("Docker and test.mysql.url are unavailable");
    }
}
