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
public enum IdentifierTypeEnums {

    GHSA("GHSA"),
    CVE("CVE")
    ;

    private String type;

    private final static Map<String, IdentifierTypeEnums> CONSTANTS = new HashMap<>();

    static {
        for (IdentifierTypeEnums type : IdentifierTypeEnums.values()) {
            CONSTANTS.put(type.type, type);
        }
    }

    IdentifierTypeEnums(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    @JsonValue
    public String value() {
        return this.type;
    }

    @Override
    public String toString() {
        return this.type;
    }

    @JsonCreator
    public static IdentifierTypeEnums fromValue(String value) {
        for (IdentifierTypeEnums type : IdentifierTypeEnums.values()) {
            if (type.type.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
