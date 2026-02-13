package pers.eastwind.billmanager.servicebill.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.servicebill.model.MonthSumAmount;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatisticService 集成测试
 */
class StatisticServiceTest extends BaseServiceTest {

    @Autowired
    private StatisticService statisticService;

    @Autowired
    private ServiceBillBizService serviceBillBizService;

    @Test
    @DisplayName("测试按状态统计单据数量")
    void shouldCountBillsByState() {
        createTestServiceBill("统计测试项目1");
        createTestServiceBill("统计测试项目2");

        Map<ServiceBillState, Long> result = statisticService.countBillsByState();

        assertNotNull(result);
        assertTrue(result.containsKey(ServiceBillState.CREATED));
    }

    @Test
    @DisplayName("测试按月份统计单据金额")
    void shouldSumReceiveAmountByMonth() {
        createTestServiceBill("月度统计测试1");
        createTestServiceBill("月度统计测试2");

        List<MonthSumAmount> result = statisticService.sumReceiveAmountByMonth();

        assertNotNull(result);
    }

    /**
     * 创建测试用的服务单
     */
    private void createTestServiceBill(String projectName) {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName(projectName);
        bill.setProjectAddress("测试地址");
        bill.setTotalAmount(new BigDecimal("1000.00"));
        bill.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
        detail.setDevice("测试设备");
        detail.setUnitPrice(new BigDecimal("1000.00"));
        detail.setQuantity(BigDecimal.ONE);
        detail.setSubtotal(new BigDecimal("1000.00"));

        bill.setDetails(List.of(detail));
        bill.setAttachments(new ArrayList<>());

        serviceBillBizService.create(bill);
    }
}
