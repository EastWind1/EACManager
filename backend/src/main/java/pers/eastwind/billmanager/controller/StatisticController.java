package pers.eastwind.billmanager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.model.common.ServiceBillState;
import pers.eastwind.billmanager.model.dto.MonthSumAmount;
import pers.eastwind.billmanager.service.ServiceBillService;

import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/api/statistic")
public class StatisticController {

    private final ServiceBillService serviceBillService;

    public StatisticController(ServiceBillService serviceBillService) {
        this.serviceBillService = serviceBillService;
    }

    /**
     * 获取不同状态的单据数量
     */
    @GetMapping("/billCountByState")
    public Map<ServiceBillState, Long> countBillsByState() {
        return serviceBillService.countBillsByState();
    }

    /**
     * 按月份统计应收和已收单据金额总和
     */
    @GetMapping("/billTotalAmountGroupByMonth")
    public List<MonthSumAmount> sumBillReceiveAmountByMonth() {
        return serviceBillService.sumReceiveAmountByMonth();
    }
}
