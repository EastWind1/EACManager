package pers.eastwind.billmanager.common.util;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * 文本日期解析：依次尝试 ISO、中文日期等格式，解析失败返回 null
 */
public class DateParseUtil {

    private DateParseUtil() {
    }

    public static Instant parseDateString(String text) {
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_INSTANT,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy年M月d日"),
                DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                TemporalAccessor accessor = formatter.parse(text);
                try {
                    return Instant.from(accessor);
                } catch (DateTimeException e) {
                    // 尝试补全时间
                    LocalDate date = LocalDate.from(accessor);
                    return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
