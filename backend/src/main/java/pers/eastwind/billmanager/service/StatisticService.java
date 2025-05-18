package pers.eastwind.billmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.model.common.ServiceBillState;
import pers.eastwind.billmanager.repository.ServiceBillRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StatisticService {
    private final ServiceBillRepository serviceBillRepository;

    public StatisticService(ServiceBillRepository serviceBillRepository) {
        this.serviceBillRepository = serviceBillRepository;
    }

    /**
     * 统计不同状态的单据数量
     *
     * @return 包含各状态数量的 Map
     */
    public Map<ServiceBillState, Long> countBillsByState() {
        List<Object[]> results = serviceBillRepository.countByState();
        Map<ServiceBillState, Long> stateCountMap = new HashMap<>();

        for (Object[] result : results) {
            ServiceBillState state = (ServiceBillState) result[0];
            Long count = (Long) result[1];
            stateCountMap.put(state, count);
        }

        return stateCountMap;
    }

    /**
     * 按月份统计非新建状态的单据金额总和
     *
     * @return 每个月份与对应金额的 Map
     */
    public Map<YearMonth, BigDecimal> sumTotalAmountByMonth() {
        List<Object[]> results = serviceBillRepository.sumTotalAmountByMonth();
        Map<YearMonth, BigDecimal> monthAmountMap = new HashMap<>();

        for (Object[] result : results) {
            LocalDate processedDate = (LocalDate) result[0];
            BigDecimal totalAmount = (BigDecimal) result[1];

            YearMonth yearMonth = YearMonth.from(processedDate);

            monthAmountMap.merge(yearMonth, totalAmount, BigDecimal::add);
        }

        return monthAmountMap;
    }
}
