package pers.eastwind.billmanager.servicebill.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.servicebill.service.BillStatisticService;

import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/api/serviceBill")
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class BillStatisticController {

    private final BillStatisticService billStatisticService;

    public BillStatisticController(BillStatisticService billStatisticService) {
        this.billStatisticService = billStatisticService;
    }

    /**
     * 获取不同状态的单据数量
     */

    @GetMapping("/countByState")
    public Map<ServiceBillState, Long> countBillsByState() {
        return billStatisticService.countBillsByState();
    }

    /**
     * 按月份统计应收和已收单据金额总和
     */
    @GetMapping("/totalAmountGroupByMonth")
    public List<MonthSumAmount> sumBillReceiveAmountByMonth() {
        return billStatisticService.sumReceiveAmountByMonth();
    }
}
