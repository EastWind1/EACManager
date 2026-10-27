package pers.eastwind.billmanager.reimburse.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.common.model.MonthAmountRow;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.reimburse.model.ReimburseState;
import pers.eastwind.billmanager.reimburse.model.ReimburseStateCount;
import pers.eastwind.billmanager.reimburse.repository.ReimburseStatisticRepository;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReimburseStatisticService {
    private final ReimburseStatisticRepository reimburseStatisticRepository;

    public ReimburseStatisticService(ReimburseStatisticRepository reimburseStatisticRepository) {
        this.reimburseStatisticRepository = reimburseStatisticRepository;
    }

    @Cacheable(value = "reimburse_statistic", key = "'countReimburseByState'")
    public Map<ReimburseState, Long> countReimburseByState() {
        Map<ReimburseState, Long> stateCountMap = new HashMap<>();
        for (ReimburseStateCount row : reimburseStatisticRepository.countByState()) {
            stateCountMap.put(row.state(), row.count());
        }
        return stateCountMap;
    }

    /**
     * 仅统计近一年，按报销日期的月份聚合金额。
     */
    @Cacheable(value = "reimburse_statistic", key = "'sumAmountByMonth'")
    public List<MonthSumAmount> sumAmountByMonth() {
        Instant preYear = Instant.now().minus(365, ChronoUnit.DAYS);
        List<MonthAmountRow> results = reimburseStatisticRepository.sumAmountGroupByMonth(preYear, Instant.now());
        List<MonthSumAmount> rows = new ArrayList<>();

        for (MonthAmountRow result : results) {
            rows.add(new MonthSumAmount(YearMonth.of(result.year(), result.month()).toString(), result.amount()));
        }
        rows.sort(Comparator.comparing(MonthSumAmount::month));
        return rows;
    }
}
