package com.aimes.service.coze;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Coze 子包内共享常量，避免各 Service 重复定义。 */
public final class CozeConstants {

    public static final Pattern ORDER_NO_PATTERN = Pattern.compile("WO-\\d{4}-\\d{3}");

    public static final int SESSION_HISTORY_TURNS = 5;
    public static final int SESSION_HISTORY_MAX_CHARS = 500;

    public static final DateTimeFormatter SCHEDULING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final int DEFAULT_DISPATCH_HOURS = 2;

    private CozeConstants() {
    }
}
