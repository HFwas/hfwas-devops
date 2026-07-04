package com.hfwas.devops.pm.common;

import java.util.Objects;

/** Compares snowflake IDs tolerant of JS Number precision loss. */
public final class IdUtils {

    private IdUtils() {
    }

    public static boolean sameId(Long a, Long b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.doubleValue() == b.doubleValue();
    }
}
