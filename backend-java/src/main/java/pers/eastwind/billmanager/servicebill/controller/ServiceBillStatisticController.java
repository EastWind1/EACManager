package pers.eastwind.billmanager.servicebill.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.servicebill.service.ServiceBillStatisticService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/serviceBill")
@PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).STATISTIC.getCode())")
public class ServiceBillStatisticController {

    private final ServiceBillStatisticService servicebillStatisticService;

    public ServiceBillStatisticController(ServiceBillStatisticService serviceBillStatisticService) {
        this.servicebillStatisticService = serviceBillStatisticService;
    }

    @GetMapping("/countByState")
    public Map<ServiceBillState, Long> countBillsByState() {
        return servicebillStatisticService.countBillsByState();
    }

    @GetMapping("/totalAmountGroupByMonth")
    public List<MonthSumAmount> sumBillReceiveAmountByMonth() {
        return servicebillStatisticService.sumReceiveAmountByMonth();
    }
}
