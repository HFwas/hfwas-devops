package com.hfwas.devops.pipeline.param;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

public final class TaskKindParamVisibility {

    public static final String USER = "user";
    public static final String HIDDEN = "hidden";

    private TaskKindParamVisibility() {}

    public static boolean isHidden(String visibility) {
        return visibility != null && HIDDEN.equalsIgnoreCase(visibility.trim());
    }

    /** 读库：空值与无法识别的旧值都按对用户开放。 */
    public static String normalizeOrUser(String visibility) {
        if (isHidden(visibility)) {
            return HIDDEN;
        }
        return USER;
    }

    /** 写入：只接受 user / hidden，空值视为 user。 */
    public static String require(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return USER;
        }
        String value = visibility.trim().toLowerCase();
        if (USER.equals(value) || HIDDEN.equals(value)) {
            return value;
        }
        throw BizException.of(ResultCode.BAD_REQUEST, "参数可见性只支持 user 或 hidden");
    }
}
