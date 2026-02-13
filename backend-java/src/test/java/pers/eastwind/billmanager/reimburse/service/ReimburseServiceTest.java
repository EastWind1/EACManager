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
