package pers.eastwind.billmanager.servicebill.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.common.model.MonthAmountRow;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.servicebill.model.ServiceBillStateCount;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillStatisticRepository;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ServiceBillStatisticService {
    private final ServiceBillStatisticRepository serviceBillStatisticRepository;

    public ServiceBillStatisticService(ServiceBillStatisticRepository serviceBillStatisticRepository) {
        this.serviceBillStatisticRepository = serviceBillStatisticRepository;
    }

    @Cacheable(value = "serviceBill_statistic", key = "'countBillsByState'")
    public Map<ServiceBillState, Long> countBillsByState() {
        Map<ServiceBillState, Long> stateCountMap = new HashMap<>();
        for (ServiceBillStateCount row : serviceBillStatisticRepository.countByState()) {
            stateCountMap.put(row.state(), row.count());
        }
        return stateCountMap;
    }


    @Cacheable(value = "serviceBill_statistic", key = "'sumReceiveAmountByMonth'")
    public List<MonthSumAmount> sumReceiveAmountByMonth() {

        Instant preYear = Instant.now().minus(365, ChronoUnit.DAYS);
        List<MonthAmountRow> results = serviceBillStatisticRepository.sumAmountByStateGroupByMonth(
                List.of(ServiceBillState.PROCESSED, ServiceBillState.FINISHED),
                preYear, Instant.now());
        List<MonthSumAmount> rows = new ArrayList<>();

        for (MonthAmountRow result : results) {
            rows.add(new MonthSumAmount(YearMonth.of(result.year(), result.month()).toString(), result.amount()));
        }
        rows.sort(Comparator.comparing(MonthSumAmount::month));
        return rows;
    }
}
