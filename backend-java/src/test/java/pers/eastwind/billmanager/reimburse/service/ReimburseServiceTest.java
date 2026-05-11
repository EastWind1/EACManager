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
import pers.eastwind.billmanager.reimburse.model.ReimbursementDTO;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReimburseService 集成测试
 */
class ReimburseServiceTest extends BaseServiceTest {

    @Autowired
    private ReimburseService reimburseService;

    @Test
    @DisplayName("测试根据ID查找报销单")
    void shouldFindReimbursementById() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);

        ReimbursementDTO foundReimburse = reimburseService.findById(createdReimburse.getId());

        assertNotNull(foundReimburse);
        assertEquals(createdReimburse.getId(), foundReimburse.getId());
        assertEquals(createdReimburse.getSummary(), foundReimburse.getSummary());
    }

    @Test
    @DisplayName("测试创建报销单")
    void shouldCreateReimbursement() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);
        assertNotNull(createdReimburse);
        assertNotNull(createdReimburse.getId());
        assertNotNull(createdReimburse.getNumber()); // 单号应该自动生成
        assertEquals(newReimburse.getSummary(), createdReimburse.getSummary());
        assertEquals(ReimburseState.CREATED, createdReimburse.getState());
    }

    @Test
    @DisplayName("测试更新报销单")
    void shouldUpdateReimbursement() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);

        createdReimburse.setSummary("更新后的摘要");
        createdReimburse.setTotalAmount(new BigDecimal("2000.00"));
        ReimbursementDTO updatedReimburse = reimburseService.update(createdReimburse);

        assertNotNull(updatedReimburse);
        assertEquals("更新后的摘要", updatedReimburse.getSummary());
        assertEquals(new BigDecimal("2000.00"), updatedReimburse.getTotalAmount());
    }

    @Test
    @DisplayName("测试根据条件查询报销单")
    void shouldFindByParam() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        reimburseService.create(newReimburse);

        ReimburseQueryParam param = new ReimburseQueryParam();
        param.setSummary("集成测试");

        Page<ReimbursementDTO> result = reimburseService.findByParam(param);

        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1);
    }

    @Test
    @DisplayName("测试批量删除报销单")
    void shouldDeleteReimbursements() {
        ReimbursementDTO reimburse1 = createTestReimbursement();
        ReimbursementDTO reimburse2 = createTestReimbursement();
        ReimbursementDTO created1 = reimburseService.create(reimburse1);
        ReimbursementDTO created2 = reimburseService.create(reimburse2);

        List<Integer> idsToDelete = Arrays.asList(created1.getId(), created2.getId());

        ActionsResult<Integer, Void> result = reimburseService.delete(idsToDelete);

        assertNotNull(result);
        assertThrows(RuntimeException.class, () -> reimburseService.findById(created1.getId()));
    }

    @Test
    @DisplayName("测试处理报销单")
    void shouldProcessReimbursements() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);
        List<Integer> idsToProcess = List.of(createdReimburse.getId());

        ActionsResult<Integer, Void> result = reimburseService.process(idsToProcess);

        assertNotNull(result);
        ReimbursementDTO processedReimburse = reimburseService.findById(createdReimburse.getId());
        assertEquals(ReimburseState.PROCESSING, processedReimburse.getState());
    }

    @Test
    @DisplayName("测试完成报销单")
    void shouldFinishReimbursements() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);
        List<Integer> idsToProcess = List.of(createdReimburse.getId());
        reimburseService.process(idsToProcess);

        ActionsResult<Integer, Void> result = reimburseService.finish(idsToProcess);

        assertNotNull(result);
        ReimbursementDTO finishedReimburse = reimburseService.findById(createdReimburse.getId());
        assertEquals(ReimburseState.FINISHED, finishedReimburse.getState());
    }

    @Test
    @DisplayName("测试导出报销单")
    void shouldExportReimbursements() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);
        List<Integer> idsToExport = List.of(createdReimburse.getId());

        Path exportPath = reimburseService.export(idsToExport);
        assertNotNull(exportPath);
    }

    @Test
    @DisplayName("测试查找不存在的报销单")
    void shouldNotFindNonExistentReimbursement() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.findById(999999); // 假设这个ID不存在
        });
    }

    @Test
    @DisplayName("测试使用无效ID更新报销单")
    void shouldNotUpdateWithInvalidID() {
        ReimbursementDTO invalidReimburse = new ReimbursementDTO();
        invalidReimburse.setId(0); // 无效ID
        invalidReimburse.setSummary("无效ID的更新");
        
        assertThrows(RuntimeException.class, () -> {
            reimburseService.update(invalidReimburse);
        });
    }

    @Test
    @DisplayName("测试删除非创建状态的报销单")
    void shouldNotDeleteInvalidStateReimbursement() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);
        
        // 先提交，使其变为处理中状态
        List<Integer> idsToProcess = List.of(createdReimburse.getId());
        ActionsResult<Integer, Void> processResult = reimburseService.process(idsToProcess);
        assertEquals(1, processResult.getSuccessCount());
        assertEquals(0, processResult.getFailCount());
        
        // 尝试删除非创建状态的记录
        ActionsResult<Integer, Void> deleteResult = reimburseService.delete(List.of(createdReimburse.getId()));
        assertEquals(0, deleteResult.getSuccessCount());
        assertEquals(1, deleteResult.getFailCount());
        assertEquals(1, deleteResult.getResults().size());
        assertFalse(deleteResult.getResults().get(0).isSuccess());
        assertTrue(deleteResult.getResults().get(0).getMessage().contains("非创建状态不能删除"));
    }

    @Test
    @DisplayName("测试无效的状态转换")
    void shouldHandleInvalidStateTransitions() {
        ReimbursementDTO newReimburse = createTestReimbursement();
        ReimbursementDTO createdReimburse = reimburseService.create(newReimburse);
        List<Integer> ids = List.of(createdReimburse.getId());
        
        // 测试从创建直接到完成（跳过处理状态）
        ActionsResult<Integer, Void> finishResult1 = reimburseService.finish(ids);
        assertEquals(0, finishResult1.getSuccessCount());
        assertEquals(1, finishResult1.getFailCount());
        assertTrue(finishResult1.getResults().get(0).getMessage().contains("非处理状态不能完成"));

        // 提交到处理状态
        ActionsResult<Integer, Void> processResult = reimburseService.process(ids);
        assertEquals(1, processResult.getSuccessCount());
        assertEquals(0, processResult.getFailCount());

        // 再次尝试提交（已经是处理状态）
        ActionsResult<Integer, Void> processResult2 = reimburseService.process(ids);
        assertEquals(0, processResult2.getSuccessCount());
        assertEquals(1, processResult2.getFailCount());
        assertTrue(processResult2.getResults().get(0).getMessage().contains("非创建状态不能提交"));

        // 完成后再次完成
        ActionsResult<Integer, Void> finishResult2 = reimburseService.finish(ids);
        assertEquals(1, finishResult2.getSuccessCount());
        assertEquals(0, finishResult2.getFailCount());

        // 尝试从完成状态再次完成
        ActionsResult<Integer, Void> finishResult3 = reimburseService.finish(ids);
        assertEquals(0, finishResult3.getSuccessCount());
        assertEquals(1, finishResult3.getFailCount());
        assertTrue(finishResult3.getResults().get(0).getMessage().contains("非处理状态不能完成"));
    }

    @Test
    @DisplayName("测试删除空ID列表")
    void shouldNotDeleteEmptyIDs() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.delete(new ArrayList<>());
        });
    }

    @Test
    @DisplayName("测试处理空ID列表")
    void shouldNotProcessEmptyIDs() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.process(new ArrayList<>());
        });
    }

    @Test
    @DisplayName("测试完成空ID列表")
    void shouldNotFinishEmptyIDs() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.finish(new ArrayList<>());
        });
    }

    @Test
    @DisplayName("测试导出空ID列表")
    void shouldNotExportEmptyIDs() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.export(new ArrayList<>());
        });
    }

    @Test
    @DisplayName("测试导出不存在的记录")
    void shouldNotExportNonExistentRecords() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.export(List.of(999999));
        });
    }

    @Test
    @DisplayName("测试使用无效参数查询")
    void shouldNotFindByParamWithInvalidParam() {
        assertThrows(RuntimeException.class, () -> {
            reimburseService.findByParam(null);
        });
    }

    /**
     * 创建测试用的报销单DTO
     */
    private ReimbursementDTO createTestReimbursement() {
        ReimbursementDTO reimbursement = new ReimbursementDTO();
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