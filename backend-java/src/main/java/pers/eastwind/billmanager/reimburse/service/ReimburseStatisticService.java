package pers.eastwind.billmanager.reimburse.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.reimburse.model.ReimburseState;
import pers.eastwind.billmanager.reimburse.repository.ReimburseStatisticRepository;

import java.math.BigDecimal;
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

    /**
     * 统计不同状态的报销单数量
     *
     * @return 包含各状态数量的 Map
     */
    @Cacheable(value = "reimburse_statistic", key = "'countReimburseByState'")
    public Map<ReimburseState, Long> countReimburseByState() {
        List<Object[]> results = reimburseStatisticRepository.countByState();
        Map<ReimburseState, Long> stateCountMap = new HashMap<>();

        for (Object[] result : results) {
            ReimburseState state = (ReimburseState) result[0];
            Long count = (Long) result[1];
            stateCountMap.put(state, count);
        }

        return stateCountMap;
    }

    /**
     * 按月份统计近一年报销金额总和
     *
     * @return 每个月份与对应金额的列表
     */
    @Cacheable(value = "reimburse_statistic", key = "'sumAmountByMonth'")
    public List<MonthSumAmount> sumAmountByMonth() {
        Instant preYear = Instant.now().minus(365, ChronoUnit.DAYS);
        List<Object[]> results = reimburseStatisticRepository.sumAmountGroupByMonth(preYear, Instant.now());
        List<MonthSumAmount> rows = new ArrayList<>();

        for (Object[] result : results) {
            int rowYear = ((Number) result[0]).intValue();
            int month = ((Number) result[1]).intValue();
            BigDecimal totalAmount = new BigDecimal(result[2].toString());

            rows.add(new MonthSumAmount(YearMonth.of(rowYear, month).toString(), totalAmount));
        }
        rows.sort(Comparator.comparing(MonthSumAmount::month));
        return rows;
    }
}
