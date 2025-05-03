package com.eastwind.EACAfterSaleMgr.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import org.springframework.stereotype.Service;

/**
 * OCR 服务
 */
@Service
public class OcrService {
    InferenceEngine engine;
    public OcrService() {
        engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
    }

    /**
     * 执行 OCR 识别
     * @param imagePath 图片路径
     */
    public OcrResult runOcr(String imagePath) {
        return engine.runOcr(imagePath);
    }
}
