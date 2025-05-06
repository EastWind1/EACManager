package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.service.ServiceBillService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务单控制器
 */
@RestController
@RequestMapping("/api/serviceBill")
public class ServiceBillController {
    private final ServiceBillService serviceBillService;

    public ServiceBillController(ServiceBillService serviceBillService) {
        this.serviceBillService = serviceBillService;
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
}
