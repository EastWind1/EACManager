package pers.eastwind.billmanager.servicebill.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.security.util.AuthUtil;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillQueryParam;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceBillService 集成测试
 */
class ServiceBillActionServiceTest extends BaseServiceTest {

    @Autowired
    private ServiceBillActionService serviceBillActionService;

    @Autowired
    private ServiceBillQueryService queryService;

    @Autowired
    private UserService userService;

    @PersistenceContext
    private EntityManager entityManager;

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
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        ServiceBillDTO foundBill = queryService.findFullById(createdBill.getId());

        assertNotNull(foundBill);
        assertEquals(createdBill.getId(), foundBill.getId());
        assertEquals(createdBill.getProjectName(), foundBill.getProjectName());
    }

    @Test
    @DisplayName("测试创建服务单")
    void shouldCreateServiceBill() {
        ServiceBillDTO newBill = createTestServiceBill();

        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

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
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        createdBill.setProjectName("更新后的项目名称");
        ServiceBillDTO updatedBill = serviceBillActionService.update(createdBill);

        assertNotNull(updatedBill);
        assertEquals("更新后的项目名称", updatedBill.getProjectName());
    }

    @Test
    @DisplayName("测试根据条件查询服务单")
    void shouldFindByParam() {
        ServiceBillDTO newBill = createTestServiceBill();
        serviceBillActionService.create(newBill);

        ServiceBillQueryParam param = new ServiceBillQueryParam();
        param.setProjectName("集成测试");

        Page<ServiceBillDTO> result = queryService.findByParam(param);

        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1);
    }

    @Test
    @DisplayName("测试批量删除服务单")
    void shouldDeleteServiceBills() {
        ServiceBillDTO bill1 = createTestServiceBill();
        ServiceBillDTO bill2 = createTestServiceBill();
        ServiceBillDTO created1 = serviceBillActionService.create(bill1);
        ServiceBillDTO created2 = serviceBillActionService.create(bill2);

        List<Integer> idsToDelete = Arrays.asList(created1.getId(), created2.getId());

        ActionsResult<Integer, Void> result = serviceBillActionService.delete(idsToDelete);

        assertNotNull(result);
        assertThrows(RuntimeException.class, () -> queryService.findFullById(created1.getId()));
        assertThrows(RuntimeException.class, () -> queryService.findFullById(created2.getId()));
    }

    @Test
    @DisplayName("测试批量处理服务单")
    void shouldProcessServiceBills() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);
        List<Integer> idsToProcess = List.of(createdBill.getId());

        ActionsResult<Integer, Void> result = serviceBillActionService.process(idsToProcess);

        assertNotNull(result);
        ServiceBillDTO processedBill = queryService.findFullById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSING, processedBill.getState());
    }

    @Test
    @DisplayName("测试标记服务单为已处理")
    void shouldMarkServiceBillsAsProcessed() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);
        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess); // 先标记为处理中

        Instant processedDate = Instant.now();

        ActionsResult<Integer, Void> result = serviceBillActionService.processed(idsToProcess, processedDate);

        assertNotNull(result);
        ServiceBillDTO processedBill = queryService.findFullById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSED, processedBill.getState());
    }

    @Test
    @DisplayName("测试完成服务单")
    void shouldFinishServiceBills() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);
        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);
        Instant cur = Instant.now();
        serviceBillActionService.processed(idsToProcess, cur);

        ActionsResult<Integer, Void> result = serviceBillActionService.finish(idsToProcess, cur);
        assertNotNull(result);
        ServiceBillDTO finishedBill = queryService.findFullById(createdBill.getId());
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

        assertThrows(RuntimeException.class, () -> serviceBillActionService.create(invalidBill));

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

        assertThrows(RuntimeException.class, () -> serviceBillActionService.create(invalidBill2));
    }

    @Test
    @DisplayName("测试使用空参数查询")
    void shouldNotFindWithEmptyParam() {
        assertThrows(RuntimeException.class, () -> queryService.findByParam(null));
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

        assertThrows(RuntimeException.class, () -> serviceBillActionService.create(bill));
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

        assertThrows(RuntimeException.class, () -> serviceBillActionService.update(bill));
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

        assertThrows(RuntimeException.class, () -> serviceBillActionService.update(bill));
    }

    @Test
    @DisplayName("测试查找不存在的ID")
    void shouldNotFindNonExistentID() {
        assertThrows(RuntimeException.class, () -> queryService.findFullById(999999));
    }

    @Test
    @DisplayName("测试删除非创建状态的服务单")
    void shouldNotDeleteNonCreatedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillActionService.delete(List.of(createdBill.getId()));
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
        assertThrows(RuntimeException.class, () -> serviceBillActionService.process(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试处理非创建状态的服务单")
    void shouldNotProcessNonCreatedStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillActionService.process(idsToProcess);
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
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        Instant processedDate = Instant.now();

        ActionsResult<Integer, Void> result = serviceBillActionService.processed(idsToProcess, processedDate);
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
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        Instant cur = Instant.now();
        ActionsResult<Integer, Void> result = serviceBillActionService.finish(List.of(createdBill.getId()), cur);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非处理完成状态的单据不能完成"));
    }

    @Test
    @DisplayName("测试删除空ID列表")
    void shouldNotDeleteWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillActionService.delete(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试处理完成空ID列表")
    void shouldNotProcessedWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillActionService.processed(new ArrayList<>(), Instant.now()));
    }

    @Test
    @DisplayName("测试完成空ID列表")
    void shouldNotFinishWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> serviceBillActionService.finish(new ArrayList<>(), Instant.now()));
    }

    @Test
    @DisplayName("测试取消处理")
    void shouldCancelProcessFromProcessing() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillActionService.cancelProcess(idsToProcess);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        ServiceBillDTO canceledBill = queryService.findFullById(createdBill.getId());
        assertEquals(ServiceBillState.CREATED, canceledBill.getState());
        assertNull(canceledBill.getProcessedDate());
    }

    @Test
    @DisplayName("测试取消处理完成")
    void shouldCancelProcessedFromProcessed() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);

        Instant processedDate = Instant.now();
        serviceBillActionService.processed(idsToProcess, processedDate);

        ActionsResult<Integer, Void> result = serviceBillActionService.cancelProcessed(idsToProcess);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        ServiceBillDTO canceledBill = queryService.findFullById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSING, canceledBill.getState());
        assertNull(canceledBill.getProcessedDate());
    }

    @Test
    @DisplayName("测试取消完成")
    void shouldCancelFinishFromFinished() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);
        Instant processedDate = Instant.now();
        serviceBillActionService.processed(idsToProcess, processedDate);
        serviceBillActionService.finish(idsToProcess, processedDate);

        ActionsResult<Integer, Void> result = serviceBillActionService.cancelFinish(idsToProcess);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        ServiceBillDTO canceledBill = queryService.findFullById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSED, canceledBill.getState());
        assertNull(canceledBill.getFinishedDate());
    }

    @Test
    @DisplayName("对非处理中状态执行取消处理操作")
    void shouldNotCancelProcessNonProcessingStateBill() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        ActionsResult<Integer, Void> result = serviceBillActionService.cancelProcess(List.of(createdBill.getId()));
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
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);

        ActionsResult<Integer, Void> result = serviceBillActionService.cancelProcessed(idsToProcess);
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
        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);

        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillActionService.process(idsToProcess);
        Instant processedDate = Instant.now();
        serviceBillActionService.processed(idsToProcess, processedDate);

        ActionsResult<Integer, Void> result = serviceBillActionService.cancelFinish(idsToProcess);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());

        assertEquals(1, result.getResults().size());
        assertFalse(result.getResults().getFirst().getSuccess());
        assertTrue(result.getResults().getFirst().getMessage().contains("非完成状态的单据不能取消完成"));
    }

    @Test
    @DisplayName("更新服务单时明细就地更新，被移除的明细真正删除")
    void shouldUpdateDetailsInPlaceAndRemoveOrphans() {
        ServiceBillDTO newBill = createTestServiceBill();
        ServiceBillDetailDTO secondDetail = new ServiceBillDetailDTO();
        secondDetail.setDevice("测试设备二");
        secondDetail.setUnitPrice(new BigDecimal("500.00"));
        secondDetail.setQuantity(BigDecimal.ONE);
        secondDetail.setSubtotal(new BigDecimal("500.00"));
        newBill.setDetails(new ArrayList<>(List.of(newBill.getDetails().getFirst(), secondDetail)));
        newBill.setTotalAmount(new BigDecimal("2500.00"));

        ServiceBillDTO createdBill = serviceBillActionService.create(newBill);
        entityManager.flush();
        entityManager.clear();
        List<Integer> createdDetailIds = detailIds(createdBill.getId());
        assertEquals(2, createdDetailIds.size(), "创建后应有 2 条明细且外键已写入");

        ServiceBillDTO loadedBill = queryService.findFullById(createdBill.getId());
        assertEquals(2, loadedBill.getDetails().size());

        // 保留一条并改字段、移除另一条：保留行应就地 UPDATE，移除行应真正 DELETE
        ServiceBillDetailDTO kept = loadedBill.getDetails().getFirst();
        ServiceBillDetailDTO removed = loadedBill.getDetails().getLast();
        kept.setDevice("已修改设备");
        loadedBill.getDetails().removeLast();
        loadedBill.setTotalAmount(kept.getSubtotal());

        serviceBillActionService.update(loadedBill);
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(kept.getId()), detailIds(createdBill.getId()), "保留的明细行应就地更新，ID 不变");
        assertEquals("已修改设备", detailDevice(kept.getId()), "明细字段修改应落库");
        assertEquals(0, countDetail(removed.getId()), "被移除的明细应被删除");
        assertEquals(0, countNullForeignKey(createdDetailIds), "被移除的明细不应留下外键为空的行");
    }

    @Test
    @DisplayName("管理员可以把服务单指派给其他用户")
    void shouldAssignServiceBill() {
        ServiceBillDTO bill = serviceBillActionService.create(createTestServiceBill());

        UserDTO assignee = new UserDTO();
        assignee.setUsername("assigneeTestUser");
        assignee.setPassword("User123456");
        assignee.setName("被指派用户");
        UserDTO createdAssignee = userService.create(assignee);

        UserDTO secondAssignee = new UserDTO();
        secondAssignee.setUsername("assigneeTestUser2");
        secondAssignee.setPassword("User123456");
        secondAssignee.setName("第二负责人");
        UserDTO createdSecondAssignee = userService.create(secondAssignee);

        assertTrue(queryService.findAssignees().stream()
                .anyMatch(item -> item.getId().equals(createdAssignee.getId())));

        ActionsResult<Integer, Void> result = serviceBillActionService.assign(
                List.of(bill.getId()),
                List.of(createdAssignee.getId(), createdSecondAssignee.getId()));
        assertEquals(1, result.getSuccessCount());

        ServiceBillDTO updated = queryService.findFullById(bill.getId());
        assertEquals(2, updated.getAssignees().size());
        assertTrue(updated.getAssignees().stream()
                .anyMatch(item -> item.getId().equals(createdAssignee.getId())));
        assertTrue(updated.getAssignees().stream()
                .anyMatch(item -> item.getId().equals(createdSecondAssignee.getId())));
    }

    @Test
    @DisplayName("非管理员只能访问指派给自己的服务单")
    void shouldLimitDataScopeByAssignee() {
        ServiceBillDTO adminBill = serviceBillActionService.create(createTestServiceBill());
        setUser();
        ServiceBillDTO userBill = serviceBillActionService.create(createTestServiceBill());
        Integer userId = AuthUtil.getCurUser().getId();

        assertNotNull(userBill.getAssignees());
        assertEquals(1, userBill.getAssignees().size());
        assertEquals(userId, userBill.getAssignees().getFirst().getId());
        assertDoesNotThrow(() -> queryService.findFullById(userBill.getId()));
        assertThrows(BizException.class, () -> queryService.findFullById(adminBill.getId()));

        ServiceBillQueryParam param = new ServiceBillQueryParam();
        param.setProjectName("集成测试");
        Page<ServiceBillDTO> result = queryService.findByParam(param);
        assertTrue(result.getContent().stream().allMatch(bill ->
                bill.getAssignees() != null && bill.getAssignees().stream()
                        .anyMatch(assignee -> userId.equals(assignee.getId()))));

        ActionsResult<Integer, Void> assignResult = serviceBillActionService.assign(
                List.of(adminBill.getId()), List.of(userId));
        assertEquals(0, assignResult.getSuccessCount());
        assertTrue(assignResult.getResults().getFirst().getMessage().contains("无权访问"));
    }

    /**
     * 单据下的明细 ID，按 ID 排序
     */
    @SuppressWarnings("unchecked")
    private List<Integer> detailIds(Integer billId) {
        return entityManager
                .createNativeQuery("select id from service_bill_detail where service_bill_id = :billId order by id")
                .setParameter("billId", billId)
                .getResultList();
    }

    /**
     * 明细的设备名称
     */
    private String detailDevice(Integer detailId) {
        return (String) entityManager
                .createNativeQuery("select device from service_bill_detail where id = :detailId")
                .setParameter("detailId", detailId)
                .getSingleResult();
    }

    /**
     * 明细行是否存在
     */
    private long countDetail(Integer detailId) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from service_bill_detail where id = :detailId")
                .setParameter("detailId", detailId)
                .getSingleResult()).longValue();
    }

    /**
     * 这些明细里外键为空的行数（旧映射会留下这种孤儿行）
     */
    private long countNullForeignKey(Collection<Integer> detailIds) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from service_bill_detail where service_bill_id is null and id in (:ids)")
                .setParameter("ids", detailIds)
                .getSingleResult()).longValue();
    }
}