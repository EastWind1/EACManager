package pers.eastwind.billmanager.servicebill.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.servicebill.model.MonthSumAmount;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.servicebill.service.StatisticService;

import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/api/statistic")
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class StatisticController {

    private final StatisticService statisticService;

    public StatisticController(StatisticService statisticService) {
        this.statisticService = statisticService;
    }

    /**
     * 获取不同状态的单据数量
     */

    @GetMapping("/billCountByState")
    public Map<ServiceBillState, Long> countBillsByState() {
        return statisticService.countBillsByState();
    }

    /**
     * 按月份统计应收和已收单据金额总和
     */
    @GetMapping("/billTotalAmountGroupByMonth")
    public List<MonthSumAmount> sumBillReceiveAmountByMonth() {
        return statisticService.sumReceiveAmountByMonth();
    }
}
