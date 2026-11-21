package pers.eastwind.billmanager.reimburse.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.reimburse.model.ReimburseDetailDTO;
import pers.eastwind.billmanager.reimburse.model.ReimburseQueryParam;
import pers.eastwind.billmanager.reimburse.model.ReimburseState;
import pers.eastwind.billmanager.reimburse.model.ReimburseDTO;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报销单查询与动作集成测试
 */
class ReimburseServiceTest extends BaseServiceTest {

    @Autowired
    private ReimburseQueryService queryService;

    @Autowired
    private ReimburseActionService actionService;

    @Autowired
    private ReimburseIOService reimburseIOService;

    @Test
    @DisplayName("测试根据ID查找报销单")
    void shouldFindReimbursementById() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);

        ReimburseDTO foundReimburse = queryService.findFullById(createdReimburse.getId());

        assertNotNull(foundReimburse);
        assertEquals(createdReimburse.getId(), foundReimburse.getId());
        assertEquals(createdReimburse.getSummary(), foundReimburse.getSummary());
    }

    @Test
    @DisplayName("测试创建报销单")
    void shouldCreateReimbursement() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        assertNotNull(createdReimburse);
        assertNotNull(createdReimburse.getId());
        assertNotNull(createdReimburse.getNumber()); // 单号应该自动生成
        assertEquals(newReimburse.getSummary(), createdReimburse.getSummary());
        assertEquals(ReimburseState.CREATED, createdReimburse.getState());
    }

    @Test
    @DisplayName("测试更新报销单")
    void shouldUpdateReimbursement() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);

        createdReimburse.setSummary("更新后的摘要");
        createdReimburse.setTotalAmount(new BigDecimal("2000.00"));
        ReimburseDTO updatedReimburse = actionService.update(createdReimburse);

        assertNotNull(updatedReimburse);
        assertEquals("更新后的摘要", updatedReimburse.getSummary());
        assertEquals(new BigDecimal("2000.00"), updatedReimburse.getTotalAmount());
    }

    @Test
    @DisplayName("测试根据条件查询报销单")
    void shouldFindByParam() {
        ReimburseDTO newReimburse = createTestReimbursement();
        actionService.create(newReimburse);

        ReimburseQueryParam param = new ReimburseQueryParam();
        param.setSummary("集成测试");

        Page<ReimburseDTO> result = queryService.findByParam(param);

        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1);
    }

    @Test
    @DisplayName("测试批量删除报销单")
    void shouldDeleteReimbursements() {
        ReimburseDTO reimburse1 = createTestReimbursement();
        ReimburseDTO reimburse2 = createTestReimbursement();
        ReimburseDTO created1 = actionService.create(reimburse1);
        ReimburseDTO created2 = actionService.create(reimburse2);

        List<Integer> idsToDelete = Arrays.asList(created1.getId(), created2.getId());

        ActionsResult<Integer, Void> result = actionService.delete(idsToDelete);

        assertNotNull(result);
        assertThrows(RuntimeException.class, () -> queryService.findFullById(created1.getId()));
    }

    @Test
    @DisplayName("测试处理报销单")
    void shouldProcessReimbursements() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        List<Integer> idsToProcess = List.of(createdReimburse.getId());

        ActionsResult<Integer, Void> result = actionService.process(idsToProcess);

        assertNotNull(result);
        ReimburseDTO processedReimburse = queryService.findFullById(createdReimburse.getId());
        assertEquals(ReimburseState.PROCESSING, processedReimburse.getState());
    }

    @Test
    @DisplayName("测试完成报销单")
    void shouldFinishReimbursements() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        List<Integer> idsToProcess = List.of(createdReimburse.getId());
        actionService.process(idsToProcess);

        ActionsResult<Integer, Void> result = actionService.finish(idsToProcess);

        assertNotNull(result);
        ReimburseDTO finishedReimburse = queryService.findFullById(createdReimburse.getId());
        assertEquals(ReimburseState.FINISHED, finishedReimburse.getState());
    }

    @Test
    @DisplayName("测试导出报销单")
    void shouldExportReimbursements() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        List<Integer> idsToExport = List.of(createdReimburse.getId());

        Path exportPath = reimburseIOService.export(idsToExport);
        assertNotNull(exportPath);
    }

    @Test
    @DisplayName("测试查找不存在的报销单")
    void shouldNotFindNonExistentReimbursement() {
        assertThrows(RuntimeException.class, () -> {
            queryService.findFullById(999999);
        });
    }

    @Test
    @DisplayName("测试使用无效ID更新报销单")
    void shouldNotUpdateWithInvalidID() {
        ReimburseDTO invalidReimburse = new ReimburseDTO();
        invalidReimburse.setId(0); // 无效ID
        invalidReimburse.setSummary("无效ID的更新");
        
        assertThrows(RuntimeException.class, () -> actionService.update(invalidReimburse));
    }

    @Test
    @DisplayName("测试删除非创建状态的报销单")
    void shouldNotDeleteInvalidStateReimbursement() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        
        // 先提交，使其变为处理中状态
        List<Integer> idsToProcess = List.of(createdReimburse.getId());
        ActionsResult<Integer, Void> processResult = actionService.process(idsToProcess);
        assertEquals(1, processResult.getSuccessCount());
        assertEquals(0, processResult.getFailCount());
        
        // 尝试删除非创建状态的记录
        ActionsResult<Integer, Void> deleteResult = actionService.delete(List.of(createdReimburse.getId()));
        assertEquals(0, deleteResult.getSuccessCount());
        assertEquals(1, deleteResult.getFailCount());
        assertEquals(1, deleteResult.getResults().size());
        assertFalse(deleteResult.getResults().getFirst().getSuccess());
        assertTrue(deleteResult.getResults().getFirst().getMessage().contains("非创建状态不能删除"));
    }

    @Test
    @DisplayName("测试无效的状态转换")
    void shouldHandleInvalidStateTransitions() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        List<Integer> ids = List.of(createdReimburse.getId());
        
        // 测试从创建直接到完成（跳过处理状态）
        ActionsResult<Integer, Void> finishResult1 = actionService.finish(ids);
        assertEquals(0, finishResult1.getSuccessCount());
        assertEquals(1, finishResult1.getFailCount());
        assertTrue(finishResult1.getResults().getFirst().getMessage().contains("非处理状态不能完成"));

        // 提交到处理状态
        ActionsResult<Integer, Void> processResult = actionService.process(ids);
        assertEquals(1, processResult.getSuccessCount());
        assertEquals(0, processResult.getFailCount());

        // 再次尝试提交（已经是处理状态）
        ActionsResult<Integer, Void> processResult2 = actionService.process(ids);
        assertEquals(0, processResult2.getSuccessCount());
        assertEquals(1, processResult2.getFailCount());
        assertTrue(processResult2.getResults().getFirst().getMessage().contains("非创建状态不能提交"));

        // 完成后再次完成
        ActionsResult<Integer, Void> finishResult2 = actionService.finish(ids);
        assertEquals(1, finishResult2.getSuccessCount());
        assertEquals(0, finishResult2.getFailCount());

        // 尝试从完成状态再次完成
        ActionsResult<Integer, Void> finishResult3 = actionService.finish(ids);
        assertEquals(0, finishResult3.getSuccessCount());
        assertEquals(1, finishResult3.getFailCount());
        assertTrue(finishResult3.getResults().getFirst().getMessage().contains("非处理状态不能完成"));
    }

    @Test
    @DisplayName("测试删除空ID列表")
    void shouldNotDeleteEmptyIDs() {
        assertThrows(RuntimeException.class, () -> actionService.delete(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试处理空ID列表")
    void shouldNotProcessEmptyIDs() {
        assertThrows(RuntimeException.class, () -> actionService.process(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试完成空ID列表")
    void shouldNotFinishEmptyIDs() {
        assertThrows(RuntimeException.class, () -> actionService.finish(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试取消处理状态")
    void shouldCancelProcessReimbursements() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        List<Integer> idsToProcess = List.of(createdReimburse.getId());

        // 提交到处理状态
        ActionsResult<Integer, Void> processResult = actionService.process(idsToProcess);
        assertEquals(1, processResult.getSuccessCount());
        assertEquals(0, processResult.getFailCount());

        // 验证状态为处理中
        ReimburseDTO processedReimburse = queryService.findFullById(createdReimburse.getId());
        assertEquals(ReimburseState.PROCESSING, processedReimburse.getState());

        // 取消处理（处理中 -> 新建）
        ActionsResult<Integer, Void> cancelProcessResult = actionService.cancelProcess(idsToProcess);
        assertEquals(1, cancelProcessResult.getSuccessCount());
        assertEquals(0, cancelProcessResult.getFailCount());

        // 验证状态回退到新建
        ReimburseDTO cancelledReimburse = queryService.findFullById(createdReimburse.getId());
        assertEquals(ReimburseState.CREATED, cancelledReimburse.getState());
    }

    @Test
    @DisplayName("测试取消处理状态时使用无效ID列表")
    void shouldNotCancelProcessWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> actionService.cancelProcess(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试取消处理完成状态")
    void shouldCancelFinishReimbursements() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);
        List<Integer> idsToProcess = List.of(createdReimburse.getId());

        // 提交到处理状态
        actionService.process(idsToProcess);

        // 完成到完成状态
        ActionsResult<Integer, Void> finishResult = actionService.finish(idsToProcess);
        assertEquals(1, finishResult.getSuccessCount());
        assertEquals(0, finishResult.getFailCount());

        // 验证状态为完成
        ReimburseDTO finishedReimburse = queryService.findFullById(createdReimburse.getId());
        assertEquals(ReimburseState.FINISHED, finishedReimburse.getState());

        // 取消完成（完成 -> 处理中）
        ActionsResult<Integer, Void> cancelFinishRes = actionService.cancelFinish(idsToProcess);
        assertEquals(1, cancelFinishRes.getSuccessCount());
        assertEquals(0, cancelFinishRes.getFailCount());

        // 验证状态回退到处理中
        ReimburseDTO cancelledReimburse = queryService.findFullById(createdReimburse.getId());
        assertEquals(ReimburseState.PROCESSING, cancelledReimburse.getState());
    }

    @Test
    @DisplayName("测试取消处理完成状态时使用无效ID列表")
    void shouldNotCancelFinishWithEmptyIDs() {
        assertThrows(RuntimeException.class, () -> actionService.cancelFinish(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试取消处理完成状态时使用无效状态")
    void shouldNotCancelFinishWithInvalidState() {
        ReimburseDTO newReimburse = createTestReimbursement();
        ReimburseDTO createdReimburse = actionService.create(newReimburse);

        // 创建状态不能取消完成
        ActionsResult<Integer, Void> cancelFinishRes = actionService.cancelFinish(List.of(createdReimburse.getId()));
        assertEquals(0, cancelFinishRes.getSuccessCount());
        assertEquals(1, cancelFinishRes.getFailCount());
        assertTrue(cancelFinishRes.getResults().getFirst().getMessage().contains("非完成状态不能取消"));

        // 提交到处理状态
        actionService.process(List.of(createdReimburse.getId()));

        // 处理中状态也不能取消完成
        ActionsResult<Integer, Void> cancelFinishRes2 = actionService.cancelFinish(List.of(createdReimburse.getId()));
        assertEquals(0, cancelFinishRes2.getSuccessCount());
        assertEquals(1, cancelFinishRes2.getFailCount());
        assertTrue(cancelFinishRes2.getResults().getFirst().getMessage().contains("非完成状态不能取消"));
    }

    @Test
    @DisplayName("测试导出空ID列表")
    void shouldNotExportEmptyIDs() {
        assertThrows(RuntimeException.class, () -> reimburseIOService.export(new ArrayList<>()));
    }

    @Test
    @DisplayName("测试导出不存在的记录")
    void shouldNotExportNonExistentRecords() {
        assertThrows(RuntimeException.class, () -> reimburseIOService.export(List.of(999999)));
    }

    @Test
    @DisplayName("测试使用无效参数查询")
    void shouldNotFindByParamWithInvalidParam() {
        assertThrows(RuntimeException.class, () -> queryService.findByParam(null));
    }

    /**
     * 创建测试用的报销单DTO
     */
    private ReimburseDTO createTestReimbursement() {
        ReimburseDTO reimbursement = new ReimburseDTO();
        reimbursement.setSummary("集成测试报销");
        reimbursement.setTotalAmount(new BigDecimal("1000.00"));
        reimbursement.setReimburseDate(Instant.now());
        reimbursement.setRemark("测试备注");

        ReimburseDetailDTO detail = new ReimburseDetailDTO();
        detail.setName("明细摘要");
        detail.setAmount(new BigDecimal("1000.00"));

        reimbursement.setDetails(List.of(detail));
        reimbursement.setAttachments(new ArrayList<>());

        return reimbursement;
    }
}