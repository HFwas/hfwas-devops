package com.hfwas.devops.pm.common;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * PM 业务时间统一按 UTC naive datetime 处理，不做时区换算。
 */
public final class PmDateTimeUtils {

    private static final DateTimeFormatter SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PmDateTimeUtils() {
    }

    public static LocalDateTime parseDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (raw instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text, SPACE_FORMAT);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.DATE_FORMAT_INVALID, "日期格式错误: " + text);
        }
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(SPACE_FORMAT);
    }
}
