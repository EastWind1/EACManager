package com.eastwind.EACAfterSaleMgr.controller;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.service.AttachmentService;
import com.eastwind.EACAfterSaleMgr.service.OcrService;
import com.eastwind.EACAfterSaleMgr.service.ServiceBillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * 服务单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/serviceBill")
public class ServiceBillController {
    private final ServiceBillService serviceBillService;
    private final AttachmentService attachmentService;
    private final OcrService ocrService;
    public ServiceBillController(ServiceBillService serviceBillService, AttachmentService attachmentService, OcrService ocrService) {
        this.serviceBillService = serviceBillService;
        this.attachmentService = attachmentService;
        this.ocrService = ocrService;
    }

    /**
     * 获取所有服务单
     * @return 服务单列表
     */
    @GetMapping
    public List<ServiceBillDTO> getAll() {
        return serviceBillService.findAll();

    }

    /**
     * 创建服务单
     * @param serviceBillDTO 服务单
     * @return 保存后的服务单
     */
    @PostMapping
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        return serviceBillService.create(serviceBillDTO);
    }
    /**
     * 通过文件创建
     */
    @PostMapping("import")
    public ServiceBillDTO importByFile(MultipartFile file) {
        Path tempPath = attachmentService.upload(file, attachmentService.TEMP_DIR);
        OcrResult result = ocrService.runOcr(tempPath.toString());
        return serviceBillService.generateByOcrResult(result);
    }

}
