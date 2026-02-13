package pers.eastwind.billmanager.servicebill.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillQueryParam;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceBillBizService 集成测试
 */
class ServiceBillBizServiceTest extends BaseServiceTest {

    @Autowired
    private ServiceBillBizService serviceBillBizService;

    @Test
    @DisplayName("测试根据ID查找服务单")
    void shouldFindServiceBillById() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        ServiceBillDTO foundBill = serviceBillBizService.findById(createdBill.getId());

        assertNotNull(foundBill);
        assertEquals(createdBill.getId(), foundBill.getId());
        assertEquals(createdBill.getProjectName(), foundBill.getProjectName());
    }

    @Test
    @DisplayName("测试创建服务单")
    void shouldCreateServiceBill() {
        ServiceBillDTO newBill = createTestServiceBill();

        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        assertNotNull(createdBill);
        assertNotNull(createdBill.getId());
        assertNotNull(createdBill.getNumber()); // 单号应该自动生成
        assertEquals(newBill.getProjectName(), createdBill.getProjectName());
        assertEquals(ServiceBillState.CREATED, createdBill.getState());
    }

    @Test
    @DisplayName("测试更新服务单")
    void shouldUpdateServiceBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        createdBill.setProjectName("更新后的项目名称");
        ServiceBillDTO updatedBill = serviceBillBizService.update(createdBill);

        assertNotNull(updatedBill);
        assertEquals("更新后的项目名称", updatedBill.getProjectName());
    }

    @Test
    @DisplayName("测试根据条件查询服务单")
    void shouldFindByParam() {
        ServiceBillDTO newBill = createTestServiceBill();
        serviceBillBizService.create(newBill);

        ServiceBillQueryParam param = new ServiceBillQueryParam();
        param.setProjectName("集成测试");

        Page<ServiceBillDTO> result = serviceBillBizService.findByParam(param);

        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1);
    }

    @Test
    @DisplayName("测试批量删除服务单")
    void shouldDeleteServiceBills() {
        ServiceBillDTO bill1 = createTestServiceBill();
        ServiceBillDTO bill2 = createTestServiceBill();
        ServiceBillDTO created1 = serviceBillBizService.create(bill1);
        ServiceBillDTO created2 = serviceBillBizService.create(bill2);

        List<Integer> idsToDelete = Arrays.asList(created1.getId(), created2.getId());

        ActionsResult<Integer, Void> result = serviceBillBizService.delete(idsToDelete);

        assertNotNull(result);
        assertThrows(RuntimeException.class, () -> serviceBillBizService.findById(created1.getId()));
        assertThrows(RuntimeException.class, () -> serviceBillBizService.findById(created2.getId()));
    }

    @Test
    @DisplayName("测试批量处理服务单")
    void shouldProcessServiceBills() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);
        List<Integer> idsToProcess = List.of(createdBill.getId());

        ActionsResult<Integer, Void> result = serviceBillBizService.process(idsToProcess);

        assertNotNull(result);
        ServiceBillDTO processedBill = serviceBillBizService.findById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSING, processedBill.getState());
    }

    @Test
    @DisplayName("测试标记服务单为已处理")
    void shouldMarkServiceBillsAsProcessed() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);
        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess); // 先标记为处理中

        Instant processedDate = Instant.now();

        ActionsResult<Integer, Void> result = serviceBillBizService.processed(idsToProcess, processedDate);

        assertNotNull(result);
        ServiceBillDTO processedBill = serviceBillBizService.findById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSED, processedBill.getState());
    }

    @Test
    @DisplayName("测试完成服务单")
    void shouldFinishServiceBills() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);
        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);
        Instant cur = Instant.now();
        serviceBillBizService.processed(idsToProcess, cur);

        ActionsResult<Integer, Void> result = serviceBillBizService.finish(idsToProcess, cur);

        assertNotNull(result);
        ServiceBillDTO finishedBill = serviceBillBizService.findById(createdBill.getId());
        assertEquals(ServiceBillState.FINISHED, finishedBill.getState());
    }

    /**
     * 创建测试用的服务单DTO
     */
    private ServiceBillDTO createTestServiceBill() {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName("集成测试项目");
        bill.setProjectAddress("测试地址");
        bill.setTotalAmount(new BigDecimal("2000.00"));
        bill.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
        detail.setDevice("测试设备");
        detail.setUnitPrice(new BigDecimal("1000.00"));
        detail.setQuantity(BigDecimal.valueOf(2));
        detail.setSubtotal(new BigDecimal("2000.00"));

        bill.setDetails(List.of(detail));
        bill.setAttachments(new ArrayList<>());

        return bill;
    }
}
