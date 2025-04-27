package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.service.ServiceBillService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/serviceBill")
public class ServiceBillController {
    private final ServiceBillService serviceBillService;

    public ServiceBillController(ServiceBillService serviceBillService) {
        this.serviceBillService = serviceBillService;
    }

    @GetMapping
    public List<ServiceBillDTO> getAll() {
        return serviceBillService.findAll();

    }

    @PostMapping
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        return serviceBillService.create(serviceBillDTO);
    }
}
