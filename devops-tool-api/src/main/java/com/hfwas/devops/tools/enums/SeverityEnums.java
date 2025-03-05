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
public enum SeverityEnums {
    critical("critical"),
    high("high"),
    medium("medium"),
    low("low"),
    unknown("unknown");

    private final static Map<String, SeverityEnums> CONSTANTS = new HashMap<>();
    private final String value;

    SeverityEnums(String value) {
        this.value = value;
    }

    static {
        for (SeverityEnums c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    @JsonCreator
    public static SeverityEnums fromValue(String value) {
        SeverityEnums constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    public String toString() {
        return this.value;
    }
}
