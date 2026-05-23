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

    @Test
    @DisplayName("测试金额验证错误")
    void shouldValidateAmountError() {
        ServiceBillDTO invalidBill = new ServiceBillDTO();
        invalidBill.setState(ServiceBillState.CREATED);
        invalidBill.setProjectName("测试项目");
        invalidBill.setProjectAddress("测试地址");
        invalidBill.setTotalAmount(new BigDecimal("2000.00"));
        invalidBill.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
        detail.setDevice("测试设备");
        detail.setUnitPrice(new BigDecimal("1000.00"));
        detail.setQuantity(BigDecimal.ONE);
        detail.setSubtotal(new BigDecimal("1500.00")); // 明细金额与数量不匹配

        invalidBill.setDetails(List.of(detail));
        invalidBill.setAttachments(new ArrayList<>());

        assertThrows(RuntimeException.class, () -> serviceBillBizService.create(invalidBill));

        ServiceBillDTO invalidBill2 = new ServiceBillDTO();
        invalidBill2.setState(ServiceBillState.CREATED);
        invalidBill2.setProjectName("测试项目");
        invalidBill2.setProjectAddress("测试地址");
        invalidBill2.setTotalAmount(new BigDecimal("1500.00")); // 总金额与明细不符
        invalidBill2.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail2 = new ServiceBillDetailDTO();
        detail2.setDevice("测试设备");
        detail2.setUnitPrice(new BigDecimal("1000.00"));
        detail2.setQuantity(BigDecimal.ONE);
        detail2.setSubtotal(new BigDecimal("1000.00"));

        invalidBill2.setDetails(List.of(detail2));
        invalidBill2.setAttachments(new ArrayList<>());

        assertThrows(RuntimeException.class, () -> serviceBillBizService.create(invalidBill2));
    }

    @Test
    @DisplayName("测试使用空参数查询")
    void shouldNotFindWithEmptyParam() {
        assertThrows(RuntimeException.class, () -> serviceBillBizService.findByParam(null));
    }

    @Test
    @DisplayName("测试使用已有ID创建服务单")
    void shouldNotCreateWithExistingID() {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setId(123); // 提供了ID，应该报错
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName("测试项目");
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

        assertThrows(RuntimeException.class, () -> serviceBillBizService.create(bill));
    }

    @Test
    @DisplayName("测试使用空ID更新服务单")
    void shouldNotUpdateWithEmptyID() {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName("测试项目");
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

        assertThrows(RuntimeException.class, () -> serviceBillBizService.update(bill));
    }

    @Test
    @DisplayName("测试更新不存在的服务单")
    void shouldNotUpdateNonExistentBill() {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setId(999999); // 不存在的ID
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName("测试项目");
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

        assertThrows(RuntimeException.class, () -> serviceBillBizService.update(bill));
    }

    @Test
    @DisplayName("测试查找不存在的ID")
    void shouldNotFindNonExistentID() {
        assertThrows(RuntimeException.class, () -> serviceBillBizService.findById(999999));
    }

    @Test
    @DisplayName("测试删除非创建状态的服务单")
    void shouldNotDeleteNonCreatedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillBizService.delete(List.of(createdBill.getId()));
        assertNotNull(result);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非创建状态的单据不能删除"));
    }

    @Test
    @DisplayName("测试处理空ID列表")
    void shouldNotProcessWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillBizService.process(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试处理非创建状态的服务单")
    void shouldNotProcessNonCreatedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillBizService.process(idsToProcess);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非创建状态的单据不能处理"));
    }

    @Test
    @DisplayName("测试对非处理中状态执行处理完成操作")
    void shouldNotProcessedNonProcessingStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        Instant processedDate = Instant.now();

        ActionsResult<Integer, Void> result = serviceBillBizService.processed(idsToProcess, processedDate);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非处理中状态的单据不能处理完成"));
    }

    @Test
    @DisplayName("测试对非处理完成状态执行完成操作")
    void shouldNotFinishNonProcessedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        Instant cur = Instant.now();
        ActionsResult<Integer, Void> result = serviceBillBizService.finish(List.of(createdBill.getId()), cur);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非处理完成状态的单据不能完成"));
    }

    @Test
    @DisplayName("测试删除空ID列表")
    void shouldNotDeleteWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillBizService.delete(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试处理完成空ID列表")
    void shouldNotProcessedWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillBizService.processed(new ArrayList<>(), Instant.now()));
    }

    @Test
    @DisplayName("测试完成空ID列表")
    void shouldNotFinishWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillBizService.finish(new ArrayList<>(), Instant.now()));
    }

    @Test
    @DisplayName("测试取消处理")
    void shouldCancelProcessFromProcessing() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillBizService.cancelProcess(idsToProcess);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        ServiceBillDTO canceledBill = serviceBillBizService.findById(createdBill.getId());
        assertEquals(ServiceBillState.CREATED, canceledBill.getState());
        assertNull(canceledBill.getProcessedDate());
    }

    @Test
    @DisplayName("测试取消处理完成")
    void shouldCancelProcessedFromProcessed() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);

        Instant processedDate = Instant.now();
        serviceBillBizService.processed(idsToProcess, processedDate);

        ActionsResult<Integer, Void> result = serviceBillBizService.cancelProcessed(idsToProcess);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        ServiceBillDTO canceledBill = serviceBillBizService.findById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSING, canceledBill.getState());
        assertNull(canceledBill.getProcessedDate());
    }

    @Test
    @DisplayName("测试取消完成")
    void shouldCancelFinishFromFinished() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);
        Instant processedDate = Instant.now();
        serviceBillBizService.processed(idsToProcess, processedDate);
        serviceBillBizService.finish(idsToProcess, processedDate);

        ActionsResult<Integer, Void> result = serviceBillBizService.cancelFinish(idsToProcess);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        ServiceBillDTO canceledBill = serviceBillBizService.findById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSED, canceledBill.getState());
        assertNull(canceledBill.getFinishedDate());
    }

    @Test
    @DisplayName("对非处理中状态执行取消处理操作")
    void shouldNotCancelProcessNonProcessingStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        ActionsResult<Integer, Void> result = serviceBillBizService.cancelProcess(List.of(createdBill.getId()));
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非处理中状态的单据不能取消处理"));
    }

    @Test
    @DisplayName("对非处理完成状态执行取消处理完成操作")
    void shouldNotCancelProcessedNonProcessedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillBizService.cancelProcessed(idsToProcess);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非处理完成状态的单据不能取消处理完成"));
    }

    @Test
    @DisplayName("对非完成状态执行取消完成操作")
    void shouldNotCancelFinishNonFinishedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillBizService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillBizService.process(idsToProcess);
        Instant processedDate = Instant.now();
        serviceBillBizService.processed(idsToProcess, processedDate);

        ActionsResult<Integer, Void> result = serviceBillBizService.cancelFinish(idsToProcess);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非完成状态的单据不能取消完成"));
    }
}