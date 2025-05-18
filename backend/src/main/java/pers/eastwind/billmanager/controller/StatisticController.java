package pers.eastwind.billmanager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.service.StatisticService;
import pers.eastwind.billmanager.model.common.ServiceBillState;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
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
     * 按月份统计非新建状态的单据金额总和
     */
    @GetMapping("/billTotalAmountGroupMonth")
    public Map<YearMonth, BigDecimal> sumTotalAmountByMonth() {
        return statisticService.sumTotalAmountByMonth();
    }
}
