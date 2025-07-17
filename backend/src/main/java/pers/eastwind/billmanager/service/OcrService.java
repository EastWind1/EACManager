package pers.eastwind.billmanager.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;


/**
 * OCR 服务
 */
@Service
@Slf4j
public class OcrService {
    private final InferenceEngine engine;
    private final MapRuleService mapRuleService;

    public OcrService(AttachmentService attachmentService, MapRuleService mapRuleService) {
        this.mapRuleService = mapRuleService;
        engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
    }

    /**
     * 转换图像为对象
     *
     * @param path   图像相对路径
     * @param target 目标对象
     */
    public void parseImage(Path path, Object target) {
        OcrResult ocrResult = engine.runOcr(path.toString());
        List<String> texts = ocrResult.getTextBlocks().stream().map(TextBlock::getText).toList();
        mapRuleService.executeMapRule("OCR", texts, target);
    }
}
