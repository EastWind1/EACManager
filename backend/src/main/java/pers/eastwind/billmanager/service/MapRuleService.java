package pers.eastwind.billmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.config.ConfigProperties;
import pers.eastwind.billmanager.model.dto.ImportMapRule;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 映射规则服务
 */
@Slf4j
@Service
public class MapRuleService {
    private final ConfigProperties properties;
    private final JsonMapper jsonMapper;

    public MapRuleService(ConfigProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 解析日期字符串
     *
     * @param text 字符串
     * @return 时间戳, null 表示解析失败
     */
    private Instant parseDateString(String text) {
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
        log.error("无法解析日期字符串: {}", text);
        return null;
    }

    /**
     * 向对象设置 String 值
     *
     * @param target    目标对象
     * @param fieldName 字段名称
     * @param value     值
     */
    private void setStringValue(Object target, String fieldName, String value) {
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

    /**
     * 获取映射规则
     */
    public List<ImportMapRule> getMapRule(String prefix, String className) {
        try {
            // 优先尝试从外部加载
            Path rulePath = properties.getMapRulePath().normalize().toAbsolutePath();
            Path mapPath = rulePath.resolve(prefix + "-" + className + ".json");
            InputStream mapStream;
            if (Files.exists(mapPath)) {
                mapStream = Files.newInputStream(mapPath);
            } else {
                // jar 包内扫描
                mapStream = getClass().getClassLoader().getResourceAsStream("rule/" + prefix + "-" + className + ".json");
            }
            return jsonMapper.readValue(mapStream, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("获取映射规则失败", e);
        }
    }


    /**
     * 根据正则表达式从文本中获取值
     *
     * @param text  文本
     * @param regex 正则
     * @return 值
     */
    private String getValueByRegex(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() && matcher.groupCount() >= 1 ? matcher.group(1).trim() : null;
    }

    /**
     * 执行映射规则
     *
     * @param texts  文本列表
     * @param target 目标对象
     */
    public void executeMapRule(List<ImportMapRule> mapRules, List<String> texts, Object target) {
        for (String text : texts) {
            for (ImportMapRule rule : mapRules) {
                if (text != null && text.contains(rule.keyword())) {
                    String value = getValueByRegex(text, rule.regex());
                    if (value != null) {
                        setStringValue(target, rule.field(), value);
                    }
                }
            }
        }
    }

    /**
     * 执行映射规则
     *
     * @param prefix 映射规则前缀，目前包含 OCR、Excel
     * @param texts  文本列表
     * @param target 目标对象
     */
    public void executeMapRule(String prefix, List<String> texts, Object target) {
        executeMapRule(getMapRule(prefix, target.getClass().getSimpleName()), texts, target);
    }
}
