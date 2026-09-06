package pers.eastwind.billmanager.reimburse.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.model.MonthSumAmount;
import pers.eastwind.billmanager.reimburse.model.ReimburseDetailDTO;
import pers.eastwind.billmanager.reimburse.model.ReimburseState;
import pers.eastwind.billmanager.reimburse.model.ReimbursementDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ReimburseStatisticService 统计功能集成测试
 */
class ReimburseStatisticServiceTest extends BaseServiceTest {

    @Autowired
    private ReimburseService reimburseService;

    @Autowired
    private ReimburseStatisticService reimburseStatisticService;

    @Autowired
    private CacheManager cacheManager;

    private static final String STAT_CACHE = "reimburse_statistic";

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
    @DisplayName("按状态统计报销单数量")
    void shouldCountReimburseByState() {
        evictStatCache();
        Map<ReimburseState, Long> before = reimburseStatisticService.countReimburseByState();

        createReimburse(new BigDecimal("1000.00"), ReimburseState.CREATED);
        createReimburse(new BigDecimal("2000.00"), ReimburseState.PROCESSING);
        createReimburse(new BigDecimal("3000.00"), ReimburseState.FINISHED);

        evictStatCache();
        Map<ReimburseState, Long> after = reimburseStatisticService.countReimburseByState();

        assertEquals(before.getOrDefault(ReimburseState.CREATED, 0L) + 1, after.get(ReimburseState.CREATED));
        assertEquals(before.getOrDefault(ReimburseState.PROCESSING, 0L) + 1, after.get(ReimburseState.PROCESSING));
        assertEquals(before.getOrDefault(ReimburseState.FINISHED, 0L) + 1, after.get(ReimburseState.FINISHED));
    }

    @Test
    @DisplayName("按月统计报销金额，且分布在不同月份")
    void shouldSumAmountByMonth() {
        Instant now = Instant.now();
        Instant otherMonth = Instant.now().minusSeconds(40L * 24 * 3600);

        evictStatCache();
        List<MonthSumAmount> before = reimburseStatisticService.sumAmountByMonth();
        Map<String, BigDecimal> beforeMap = toMonthMap(before);

        // 两张报销单分别落在当前月与更早的月份，金额为 1000 / 2000
        createReimburseAt(new BigDecimal("1000.00"), now);
        createReimburseAt(new BigDecimal("2000.00"), otherMonth);

        evictStatCache();
        List<MonthSumAmount> after = reimburseStatisticService.sumAmountByMonth();
        Map<String, BigDecimal> afterMap = toMonthMap(after);

        // 只有新增的两个月份产生正向增量，合计恰好为 3000
        assertEquals(0, positiveDeltaSum(beforeMap, afterMap).compareTo(new BigDecimal("3000.00")));
        assertEquals(2, positiveDeltaCount(beforeMap, afterMap));

        // 统计结果不为空，且重复调用不报错
        assertNotNull(after);
        List<MonthSumAmount> cached = reimburseStatisticService.sumAmountByMonth();
        assertEquals(after.size(), cached.size());
    }

    /**
     * 创建报销单并推进到指定状态
     */
    private ReimbursementDTO createReimburse(BigDecimal total, ReimburseState targetState) {
        ReimbursementDTO created = reimburseService.create(createRawReimburse(total, Instant.now()));
        List<Integer> ids = List.of(created.getId());
        if (targetState == ReimburseState.PROCESSING || targetState == ReimburseState.FINISHED) {
            reimburseService.process(ids);
            if (targetState == ReimburseState.FINISHED) {
                reimburseService.finish(ids);
            }
        }
        return reimburseService.findById(created.getId());
    }

    /**
     * 创建报销单，指定报销日期
     */
    private ReimbursementDTO createReimburseAt(BigDecimal total, Instant date) {
        return reimburseService.create(createRawReimburse(total, date));
    }

    private ReimbursementDTO createRawReimburse(BigDecimal total, Instant date) {
        ReimbursementDTO reimbursement = new ReimbursementDTO();
        reimbursement.setSummary("统计测试报销");
        reimbursement.setTotalAmount(total);
        reimbursement.setReimburseDate(date);
        reimbursement.setRemark("统计测试备注");

        ReimburseDetailDTO detail = new ReimburseDetailDTO();
        detail.setName("报销项目");
        detail.setAmount(total);
        reimbursement.setDetails(List.of(detail));
        reimbursement.setAttachments(new ArrayList<>());
        return reimbursement;
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
