package pers.eastwind.billmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.model.common.ServiceBillState;
import pers.eastwind.billmanager.model.dto.MonthSumAmount;
import pers.eastwind.billmanager.repository.ServiceBillRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

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
    public List<MonthSumAmount> sumTotalAmountByMonth() {

        List<Object[]> results = serviceBillRepository.sumTotalAmountByMonth();
        List<MonthSumAmount> rows = new ArrayList<>();

        for (Object[] result : results) {
            int rowYear = ((Number)result[0]).intValue();
            int month = ((Number)result[1]).intValue();
            BigDecimal totalAmount = new BigDecimal(result[2].toString());

            rows.add(new MonthSumAmount(YearMonth.of(rowYear, month).toString(), totalAmount));
        }
        rows.sort(Comparator.comparing(MonthSumAmount::month));
        return rows;
    }
}
