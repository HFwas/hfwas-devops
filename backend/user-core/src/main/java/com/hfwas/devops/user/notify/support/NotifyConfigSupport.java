package com.hfwas.devops.user.notify.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotifyConfigSupport {

    public static final String MASK = "******";

    private final ObjectMapper objectMapper;

    public String maskWebhookSecret(String configJson) {
        if (StringUtils.isBlank(configJson)) {
            return configJson;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (!(node instanceof ObjectNode objectNode)) {
                return configJson;
            }
            if (objectNode.has("secret") && StringUtils.isNotBlank(objectNode.get("secret").asText())) {
                objectNode.put("secret", MASK);
            }
            return objectMapper.writeValueAsString(objectNode);
        } catch (Exception e) {
            return configJson;
        }
    }

    public String mergeWebhookConfig(String incomingJson, String storedJson) {
        if (StringUtils.isBlank(incomingJson)) {
            return storedJson;
        }
        if (StringUtils.isBlank(storedJson)) {
            return incomingJson;
        }
        try {
            ObjectNode incoming = (ObjectNode) objectMapper.readTree(incomingJson);
            ObjectNode stored = (ObjectNode) objectMapper.readTree(storedJson);
            JsonNode secret = incoming.get("secret");
            if (secret != null && (MASK.equals(secret.asText()) || StringUtils.isBlank(secret.asText()))) {
                incoming.set("secret", stored.get("secret"));
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
