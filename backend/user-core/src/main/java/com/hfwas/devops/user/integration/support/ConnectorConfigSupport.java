package com.hfwas.devops.user.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConnectorConfigSupport {

    private static final String MASK = "******";

    private final ObjectMapper objectMapper;

    public String maskSecrets(String type, String configJson) {
        if (StringUtils.isBlank(configJson)) {
            return configJson;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (!(node instanceof ObjectNode objectNode)) {
                return configJson;
            }
            if ("ldap".equalsIgnoreCase(type) && objectNode.has("bindPassword")) {
                objectNode.put("bindPassword", MASK);
            }
            return objectMapper.writeValueAsString(objectNode);
        } catch (Exception e) {
            return configJson;
        }
    }

    /** Merge incoming config with stored config, keeping secrets when masked. */
    public String mergeConfig(String type, String incomingJson, String storedJson) {
        if (StringUtils.isBlank(incomingJson)) {
            return storedJson;
        }
        if (StringUtils.isBlank(storedJson)) {
            return incomingJson;
        }
        try {
            ObjectNode incoming = (ObjectNode) objectMapper.readTree(incomingJson);
            ObjectNode stored = (ObjectNode) objectMapper.readTree(storedJson);
            if ("ldap".equalsIgnoreCase(type)) {
                JsonNode password = incoming.get("bindPassword");
                if (password != null && (MASK.equals(password.asText()) || StringUtils.isBlank(password.asText()))) {
                    incoming.set("bindPassword", stored.get("bindPassword"));
                }
            }
            stored.fields().forEachRemaining(entry -> {
                if (!incoming.has(entry.getKey())) {
                    incoming.set(entry.getKey(), entry.getValue());
                }
            });
            return objectMapper.writeValueAsString(incoming);
        } catch (Exception e) {
            return incomingJson;
        }
    }
}
