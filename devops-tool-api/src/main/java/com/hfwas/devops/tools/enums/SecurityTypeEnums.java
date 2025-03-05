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
public enum SecurityTypeEnums {
    REVIEWED("reviewed"),
    MALWARE("malware"),
    UNREVIEWED("unreviewed");

    private final static Map<String, SecurityTypeEnums> CONSTANTS = new HashMap<>();

    static {
        for (SecurityTypeEnums c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private final String value;

    SecurityTypeEnums(String value) {
        this.value = value;
    }

    @JsonCreator
    public static SecurityTypeEnums fromValue(String value) {
        SecurityTypeEnums constant = CONSTANTS.get(value);
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
