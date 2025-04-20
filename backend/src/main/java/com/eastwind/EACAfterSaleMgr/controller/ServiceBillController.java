package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.dto.mapper.ServiceBillMapper;
import com.eastwind.EACAfterSaleMgr.entity.ServiceBill;
import com.eastwind.EACAfterSaleMgr.service.ServiceBillService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ServiceBillController {
    private final ServiceBillService serviceBillService;

    public ServiceBillController(ServiceBillService serviceBillService) {
        this.serviceBillService = serviceBillService;
    }

    @GetMapping("/serviceBill")
    public List<ServiceBillDTO> getAll() {
        List<ServiceBill> serviceBills = serviceBillService.findAll();
        List<ServiceBillDTO> res = new ArrayList<ServiceBillDTO>();
        for (ServiceBill serviceBill : serviceBills) {
            ServiceBillDTO serviceBillDTO = ServiceBillMapper.INSTANCE.toServiceBillDTO(serviceBill);
            res.add(serviceBillDTO);
        }
        return res;

    }
}
