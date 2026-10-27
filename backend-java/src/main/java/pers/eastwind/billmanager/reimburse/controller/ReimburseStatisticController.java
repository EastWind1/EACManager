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

@RestController
@RequestMapping("/api/reimburse")
@PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).STATISTIC.getCode())")
public class ReimburseStatisticController {

    private final ReimburseStatisticService reimburseStatisticService;

    public ReimburseStatisticController(ReimburseStatisticService reimburseStatisticService) {
        this.reimburseStatisticService = reimburseStatisticService;
    }

    @GetMapping("/countByState")
    public Map<ReimburseState, Long> countReimburseByState() {
        return reimburseStatisticService.countReimburseByState();
    }

    @GetMapping("/totalAmountGroupByMonth")
    public List<MonthSumAmount> sumAmountByMonth() {
        return reimburseStatisticService.sumAmountByMonth();
    }
}
