package pers.eastwind.billmanager.attach.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;
import java.util.List;


/**
 * OCR 服务
 */
@Service
@Slf4j
public class OCRService {
    private volatile InferenceEngine engine;

    /**
     * 转换图像为字符串集合
     *
     * @param path 图像相对路径
     * @return 文本列表
     */
    public List<String> parseImage(Path path) {
        // 懒加载 OCR 引擎
        if (engine == null) {
            synchronized (this) {
                if (engine == null) {
                    log.info("初始化 OCR 引擎");
                    engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
                }
            }
        }
        OcrResult ocrResult;
        long start = System.currentTimeMillis();
        try {
            ocrResult = engine.runOcr(path.toString());
        } catch (Exception e) {
            log.error("OCR 解析失败: {}", path.getFileName(), e);
            throw new BizException("OCR 解析失败: " + e.getMessage(), e);
        }
        log.info("OCR 解析完成: {} 耗时 {}ms", path.getFileName(), System.currentTimeMillis() - start);
        return ocrResult.getTextBlocks().stream().map(TextBlock::getText).toList();
    }
}
