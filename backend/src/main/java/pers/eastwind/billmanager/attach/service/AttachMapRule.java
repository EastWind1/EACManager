package pers.eastwind.billmanager.attach.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * 附件映射规则
 */
public interface AttachMapRule<T> {
    /**
     * OCR 映射是否使用当前规则
     */
    boolean canOCR(List<String> texts);

    /**
     * Excel 映射是否使用当前规则
     */
    boolean canExcel(List<List<String>> rows);
    /**
     * 从 OCR 文本映射
     * 用于图片和 PDF
     * @param texts ocr 文本
     * @return 目标对象
     */
    T mapFromOCR(List<String> texts);
    /**
     * 从 Excel 映射
     * 只支持单 sheet 页
     * @param rows Excel 内容
     * @return 目标对象
     */
    T mapFromExcel(List<List<String>> rows);

    /**
     * 解析日期字符串
     *
     * @param text 字符串
     * @return 时间戳, null 表示解析失败
     */
    static Instant parseDateString(String text) {
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
    /**
     * 设置字符串值
     *
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字符串值
     */
    static void setStringValue(Object target, String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            Class<?> type = field.getType();
            field.setAccessible(true);
            if (type == String.class) {
                field.set(target, value);
            } else if (type == Integer.class || type == int.class) {
                field.set(target, Integer.parseInt(value));
            } else if (type == Double.class || type == double.class) {
                field.set(target, Double.parseDouble(value));
            } else if (type == Float.class || type == float.class) {
                field.set(target, Float.parseFloat(value));
            } else if (type == Long.class || type == long.class) {
                field.set(target, Long.parseLong(value));
            } else if (type == Boolean.class || type == boolean.class) {
                field.set(target, Boolean.parseBoolean(value));
            } else if (type == BigDecimal.class) {
                field.set(target, new BigDecimal(value));
            } else if (type == Instant.class) {
                field.set(target, parseDateString(value));
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
    }
}
