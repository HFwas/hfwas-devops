package com.hfwas.devops.tools.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.enums
 * @date 2025/3/5
 */
public enum EcosystemEnums {
    RUBYGEMS("rubygems"),
    NPM("npm"),
    PIP("pip"),
    MAVEN("maven"),
    NUGET("nuget"),
    COMPOSER("composer"),
    GO("go"),
    RUST("rust"),
    ERLANG("erlang"),
    ACTIONS("actions"),
    PUB("pub"),
    OTHER("other"),
    SWIFT("swift");

    private final static Map<String, EcosystemEnums> CONSTANTS = new HashMap<>();

    static {
        for (EcosystemEnums c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private final String value;

    EcosystemEnums(String value) {
        this.value = value;
    }

    @JsonCreator
    public static EcosystemEnums fromValue(String value) {
        EcosystemEnums constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }
}
