package com.eastwind.EACAfterSaleMgr.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * OCR 服务
 */
@Service
public class OcrService {
    InferenceEngine engine;
    private final ObjectMapper objectMapper;
    private final AttachmentService attachmentService;

    public OcrService(ObjectMapper objectMapper, AttachmentService attachmentService) {
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;
        engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
    }

    /**
     * 执行 OCR 识别
     *
     * @param relativePath 相对附件目录路径
     */
    public OcrResult runOcr(Path relativePath) {

        return engine.runOcr(attachmentService.getAbsolutePath(relativePath).toString());
    }

    /**
     * 映射规则
     *
     * @param keyword 关键词
     * @param regex   取值正则
     * @param field   属性名
     */
    private record MapRule(String keyword, String regex, String field) {
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
     * @param ocrResult OCR 识别结果
     * @param target    目标对象
     */
    public void executeMapRule(OcrResult ocrResult, Object target) {
        String className = target.getClass().getSimpleName();
        try {
            File mapJson = ResourceUtils.getFile("classpath:rule/OCR-" + className + ".json");
            List<MapRule> mapRule = objectMapper.readValue(mapJson, new TypeReference<>() {
            });

            for (TextBlock textBlock : ocrResult.getTextBlocks()) {
                for (MapRule rule : mapRule) {
                    if (textBlock.getText().contains(rule.keyword())) {
                        String value = getValueByRegex(textBlock.getText(), rule.regex());
                        if (value != null) {
                            try {
                                Field field = target.getClass().getDeclaredField(rule.field());
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
                                } else if (type == LocalDate.class) {
                                    // 单独处理中文日期
                                    if (value.contains("日")) {
                                        field.set(target, LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy年M月d日")).atStartOfDay());
                                    } else {
                                        field.set(target, LocalDate.parse(value));
                                    }
                                }
                            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                            }
                        }
                    }
                }

            }

        } catch (IOException e) {
            throw new RuntimeException("处理映射规则失败", e);
        }
    }
}
