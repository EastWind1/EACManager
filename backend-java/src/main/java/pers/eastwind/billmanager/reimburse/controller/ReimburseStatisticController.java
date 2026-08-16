package pers.eastwind.billmanager.reimburse.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.reimburse.model.ReimburseState;
import pers.eastwind.billmanager.reimburse.service.ReimburseStatisticService;

import java.util.List;
import java.util.Map;

/**
 * 报销单统计控制器
 */
@RestController
@RequestMapping("/api/reimburse")
@PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
public class ReimburseStatisticController {

    private final ReimburseStatisticService reimburseStatisticService;

    public ReimburseStatisticController(ReimburseStatisticService reimburseStatisticService) {
        this.reimburseStatisticService = reimburseStatisticService;
    }

    /**
     * 获取不同状态的报销单数量
     */
    @GetMapping("/countByState")
    public Map<ReimburseState, Long> countReimburseByState() {
        return reimburseStatisticService.countReimburseByState();
    }

    /**
     * 按月份统计报销金额总和
     */
    @GetMapping("/totalAmountGroupByMonth")
    public List<MonthSumAmount> sumAmountByMonth() {
        return reimburseStatisticService.sumAmountByMonth();
    }
}
