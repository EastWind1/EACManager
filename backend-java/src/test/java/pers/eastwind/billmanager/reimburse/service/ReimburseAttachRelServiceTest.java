package pers.eastwind.billmanager.reimburse.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.model.AttachmentStatus;
import pers.eastwind.billmanager.attach.repository.AttachmentRepository;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.reimburse.model.ReimburseDetailDTO;
import pers.eastwind.billmanager.reimburse.model.ReimbursementDTO;
import pers.eastwind.billmanager.reimburse.repository.ReimburseAttachRelRepo;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报销单附件关联集成测试
 *
 * <p>验证附件以 namespace/唯一id 存储、上传即落库、提交后不可复用，
 * 以及关联由报销单模块自行维护。</p>
 */
class ReimburseAttachRelServiceTest extends BaseServiceTest {

    @Autowired
    private ReimburseService reimburseService;

    @Autowired
    private ReimburseAttachRelRepo reimburseAttachRelRepo;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentService attachmentService;

    /**
     * 上传一个附件
     */
    private AttachmentDTO uploadAttachment() throws IOException {
        Path tempFile = Files.createTempFile("reimburse-attach-", ".txt");
        Files.writeString(tempFile, "reimburse attach content");
        return attachmentService
                .upload(ReimburseAttachRelService.ATTACH_NAMESPACE, List.of(new FileSystemResource(tempFile)))
                .getFirst();
    }

    /**
     * 创建测试用报销单
     */
    private ReimbursementDTO createTestReimbursement(AttachmentDTO attachment) {
        ReimbursementDTO dto = new ReimbursementDTO();
        dto.setSummary("附件关联测试报销");
        dto.setTotalAmount(new BigDecimal("1000.00"));
        dto.setReimburseDate(Instant.now());

        ReimburseDetailDTO detail = new ReimburseDetailDTO();
        detail.setName("测试明细");
        detail.setAmount(new BigDecimal("1000.00"));

        dto.setDetails(List.of(detail));
        dto.setAttachments(List.of(attachment));
        return dto;
    }

    @Test
    @DisplayName("上传即落库，key 为 namespace 下的唯一标识")
    void shouldPersistAttachmentOnUpload() throws IOException {
        AttachmentDTO uploaded = uploadAttachment();

        Attachment attachment = attachmentRepository.findById(uploaded.getId()).orElseThrow();
        assertEquals(AttachmentStatus.TRANSIENT, attachment.getStatus());
        assertTrue(
                attachment.getRelativePath().startsWith(ReimburseAttachRelService.ATTACH_NAMESPACE + "/"),
                "附件 key 应位于自身命名空间下: " + attachment.getRelativePath()
        );
    }

    @Test
    @DisplayName("新增报销单附件时提交附件并建立关联")
    void shouldCreateRelationWhenAttachAdded() throws IOException {
        ReimbursementDTO created = reimburseService.create(createTestReimbursement(uploadAttachment()));

        assertEquals(1, created.getAttachments().size());
        List<Attachment> attachments = reimburseAttachRelRepo.findAttachmentsByBillId(created.getId());
        assertEquals(1, attachments.size());
        assertEquals(created.getAttachments().getFirst().getId(), attachments.getFirst().getId());
        assertEquals(AttachmentStatus.PERSISTENT, attachments.getFirst().getStatus());
    }

    @Test
    @DisplayName("移除报销单附件时清理关联与附件")
    void shouldDeleteRelationWhenAttachRemoved() throws IOException {
        ReimbursementDTO created = reimburseService.create(createTestReimbursement(uploadAttachment()));
        Integer attachId = created.getAttachments().getFirst().getId();
        assertEquals(1, reimburseAttachRelRepo.findAttachmentsByBillId(created.getId()).size());

        created.setAttachments(new ArrayList<>());
        ReimbursementDTO updated = reimburseService.update(created);

        assertTrue(updated.getAttachments().isEmpty());
        assertTrue(reimburseAttachRelRepo.findAttachmentsByBillId(created.getId()).isEmpty());
        assertTrue(attachmentRepository.findById(attachId).isEmpty());
    }

    @Test
    @DisplayName("删除报销单时清理关联记录")
    void shouldDeleteRelationWhenBillDeleted() throws IOException {
        ReimbursementDTO created = reimburseService.create(createTestReimbursement(uploadAttachment()));
        assertEquals(1, reimburseAttachRelRepo.findAttachmentsByBillId(created.getId()).size());

        reimburseService.delete(List.of(created.getId()));

        assertTrue(reimburseAttachRelRepo.findAttachmentsByBillId(created.getId()).isEmpty());
    }

    @Test
    @DisplayName("已提交的附件不能被另一张单据复用")
    void shouldRejectCommittedAttachmentReuse() throws IOException {
        ReimbursementDTO created = reimburseService.create(createTestReimbursement(uploadAttachment()));
        AttachmentDTO committed = created.getAttachments().getFirst();

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> reimburseService.create(createTestReimbursement(committed))
        );
        assertTrue(error.getMessage().contains("附件不存在或已被提交"), error.getMessage());
    }

    @Test
    @DisplayName("过期暂存附件按状态回收")
    void shouldCleanExpiredTempAttachments() throws IOException {
        AttachmentDTO uploaded = uploadAttachment();

        // 回退创建时间，模拟超过保留期
        Attachment attachment = attachmentRepository.findById(uploaded.getId()).orElseThrow();
        attachment.setCreatedDate(Instant.now().minus(8, ChronoUnit.DAYS));

        attachmentService.cleanTransientFiles();

        assertTrue(attachmentRepository.findById(uploaded.getId()).isEmpty());
    }
}
