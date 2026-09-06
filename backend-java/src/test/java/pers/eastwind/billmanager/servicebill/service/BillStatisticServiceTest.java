package pers.eastwind.billmanager.servicebill.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * BillStatisticService 统计功能集成测试
 */
class BillStatisticServiceTest extends BaseServiceTest {

    @Autowired
    private ServiceBillBizService serviceBillBizService;

    @Autowired
    private BillStatisticService billStatisticService;

    @Autowired
    private CacheManager cacheManager;

    private static final String STAT_CACHE = "serviceBill_statistic";

    /**
     * 统计结果带 Spring Cache，测量前清空缓存保证读到最新数据
     */
    private void evictStatCache() {
        Cache cache = cacheManager.getCache(STAT_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("按状态统计服务单数量")
    void shouldCountBillsByState() {
        evictStatCache();
        Map<ServiceBillState, Long> before = billStatisticService.countBillsByState();

        createBill(new BigDecimal("1000.00"), ServiceBillState.CREATED);
        createBill(new BigDecimal("2000.00"), ServiceBillState.PROCESSING);
        createBill(new BigDecimal("3000.00"), ServiceBillState.PROCESSED);

        evictStatCache();
        Map<ServiceBillState, Long> after = billStatisticService.countBillsByState();

        assertEquals(before.getOrDefault(ServiceBillState.CREATED, 0L) + 1, after.get(ServiceBillState.CREATED));
        assertEquals(before.getOrDefault(ServiceBillState.PROCESSING, 0L) + 1, after.get(ServiceBillState.PROCESSING));
        assertEquals(before.getOrDefault(ServiceBillState.PROCESSED, 0L) + 1, after.get(ServiceBillState.PROCESSED));
        assertEquals(before.getOrDefault(ServiceBillState.FINISHED, 0L), after.getOrDefault(ServiceBillState.FINISHED, 0L));
    }

    @Test
    @DisplayName("按月统计处理完成的金额，且分布在不同月份")
    void shouldSumReceiveAmountByMonth() {
        Instant now = Instant.now();
        Instant otherMonth = Instant.now().minusSeconds(40L * 24 * 3600);

        evictStatCache();
        List<MonthSumAmount> before = billStatisticService.sumReceiveAmountByMonth();
        Map<String, BigDecimal> beforeMap = toMonthMap(before);

        // 两张处理完成单据分别落在当前月与更早的月份，金额为 1000 / 2000
        createProcessedBill(new BigDecimal("1000.00"), now);
        createProcessedBill(new BigDecimal("2000.00"), otherMonth);

        evictStatCache();
        List<MonthSumAmount> after = billStatisticService.sumReceiveAmountByMonth();
        Map<String, BigDecimal> afterMap = toMonthMap(after);

        // 只有新增的两个月份产生正向增量，合计恰好为 3000
        assertEquals(0, positiveDeltaSum(beforeMap, afterMap).compareTo(new BigDecimal("3000.00")));
        assertEquals(2, positiveDeltaCount(beforeMap, afterMap));

        // 统计结果不为空，且重复调用不报错
        assertNotNull(after);
        List<MonthSumAmount> cached = billStatisticService.sumReceiveAmountByMonth();
        assertEquals(after.size(), cached.size());
    }

    /**
     * 创建服务单并推进到指定状态
     */
    private ServiceBillDTO createBill(BigDecimal total, ServiceBillState targetState) {
        ServiceBillDTO created = createRawBill(total);
        List<Integer> ids = List.of(created.getId());
        if (targetState == ServiceBillState.PROCESSING) {
            serviceBillBizService.process(ids);
        } else if (targetState == ServiceBillState.PROCESSED || targetState == ServiceBillState.FINISHED) {
            serviceBillBizService.process(ids);
            serviceBillBizService.processed(ids, Instant.now());
            if (targetState == ServiceBillState.FINISHED) {
                serviceBillBizService.finish(ids, Instant.now());
            }
        }
        return serviceBillBizService.findById(created.getId());
    }

    /**
     * 创建服务单并推进到“处理完成”，指定处理完成日期
     */
    private ServiceBillDTO createProcessedBill(BigDecimal total, Instant processedDate) {
        ServiceBillDTO created = createRawBill(total);
        List<Integer> ids = List.of(created.getId());
        ActionsResult<Integer, Void> processResult = serviceBillBizService.process(ids);
        assertEquals(1, processResult.getSuccessCount());
        serviceBillBizService.processed(ids, processedDate);
        return serviceBillBizService.findById(created.getId());
    }

    private ServiceBillDTO createRawBill(BigDecimal total) {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName("统计测试项目");
        bill.setProjectAddress("测试地址");
        bill.setTotalAmount(total);
        bill.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
        detail.setDevice("测试设备");
        detail.setUnitPrice(total);
        detail.setQuantity(BigDecimal.ONE);
        detail.setSubtotal(total);

        bill.setDetails(List.of(detail));
        bill.setAttachments(new ArrayList<>());
        return serviceBillBizService.create(bill);
    }

    private static Map<String, BigDecimal> toMonthMap(List<MonthSumAmount> rows) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (MonthSumAmount row : rows) {
            map.put(row.month(), row.amount());
        }
        return map;
    }

    private static BigDecimal positiveDeltaSum(Map<String, BigDecimal> before, Map<String, BigDecimal> after) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : after.entrySet()) {
            BigDecimal base = before.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(base) > 0) {
                sum = sum.add(entry.getValue().subtract(base));
            }
        }
        return sum;
    }

    private static int positiveDeltaCount(Map<String, BigDecimal> before, Map<String, BigDecimal> after) {
        int count = 0;
        for (Map.Entry<String, BigDecimal> entry : after.entrySet()) {
            BigDecimal base = before.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(base) > 0) {
                count++;
            }
        }
        return count;
    }
}
