package pers.eastwind.billmanager.servicebill.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.model.AttachStatus;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;

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
 * 服务单附件关联集成测试
 *
 * <p>验证附件以 namespace/唯一id 存储、上传即落库、提交后不可复用，
 * 以及关联由服务单模块自行维护。</p>
 */
class ServiceBillAttachServiceTest extends BaseServiceTest {

    @Autowired
    private ServiceBillActionService serviceBillActionService;

    @Autowired
    private ServiceBillAttachService serviceBillAttachService;

    @Autowired
    private AttachService attachService;

    /**
     * 上传一个附件
     */
    private AttachDTO uploadAttachment() throws IOException {
        Path tempFile = Files.createTempFile("service-bill-attach-", ".txt");
        Files.writeString(tempFile, "service bill attach content");
        return attachService
                .upload(ServiceBillAttachService.ATTACH_NAMESPACE, List.of(new FileSystemResource(tempFile)))
                .getFirst();
    }

    /**
     * 创建测试用服务单
     */
    private ServiceBillDTO createTestServiceBill(AttachDTO attachment) {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName("附件关联测试项目");
        bill.setProjectAddress("测试地址");
        bill.setTotalAmount(new BigDecimal("2000.00"));
        bill.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
        detail.setDevice("测试设备");
        detail.setUnitPrice(new BigDecimal("1000.00"));
        detail.setQuantity(BigDecimal.valueOf(2));
        detail.setSubtotal(new BigDecimal("2000.00"));

        bill.setDetails(List.of(detail));
        bill.setAttachments(List.of(attachment));
        return bill;
    }

    @Test
    @DisplayName("上传即落库，key 为 namespace 下的唯一标识")
    void shouldPersistAttachmentOnUpload() throws IOException {
        AttachDTO uploaded = uploadAttachment();

        assertEquals(AttachStatus.TRANSIENT, attachService.getStatus(uploaded.getId()));
        assertTrue(
                attachService.getRelativePath(uploaded.getId()).startsWith(ServiceBillAttachService.ATTACH_NAMESPACE + "/"),
                "附件 key 应位于自身命名空间下: " + attachService.getRelativePath(uploaded.getId())
        );
    }

    @Test
    @DisplayName("新增服务单附件时提交附件并建立关联")
    void shouldCreateRelationWhenAttachAdded() throws IOException {
        ServiceBillDTO created = serviceBillActionService.create(createTestServiceBill(uploadAttachment()));

        assertEquals(1, created.getAttachments().size());
        List<Attach> attachments = serviceBillAttachService.findAttachments(created.getId());
        assertEquals(1, attachments.size());
        assertEquals(created.getAttachments().getFirst().getId(), attachments.getFirst().getId());
        assertEquals(AttachStatus.PERSISTENT, attachments.getFirst().getStatus());
    }

    @Test
    @DisplayName("移除服务单附件时清理关联与附件")
    void shouldDeleteRelationWhenAttachRemoved() throws IOException {
        ServiceBillDTO created = serviceBillActionService.create(createTestServiceBill(uploadAttachment()));
        Integer attachId = created.getAttachments().getFirst().getId();
        assertEquals(1, serviceBillAttachService.findAttachments(created.getId()).size());

        created.setAttachments(new ArrayList<>());
        ServiceBillDTO updated = serviceBillActionService.update(created);

        assertTrue(updated.getAttachments().isEmpty());
        assertTrue(serviceBillAttachService.findAttachments(created.getId()).isEmpty());
        assertFalse(attachService.exists(attachId));
    }

    @Test
    @DisplayName("删除服务单时清理关联记录")
    void shouldDeleteRelationWhenBillDeleted() throws IOException {
        ServiceBillDTO created = serviceBillActionService.create(createTestServiceBill(uploadAttachment()));
        assertEquals(1, serviceBillAttachService.findAttachments(created.getId()).size());

        serviceBillActionService.delete(List.of(created.getId()));

        assertTrue(serviceBillAttachService.findAttachments(created.getId()).isEmpty());
    }

    @Test
    @DisplayName("已提交的附件不能被另一张单据复用")
    void shouldRejectCommittedAttachmentReuse() throws IOException {
        ServiceBillDTO created = serviceBillActionService.create(createTestServiceBill(uploadAttachment()));
        AttachDTO committed = created.getAttachments().getFirst();

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> serviceBillActionService.create(createTestServiceBill(committed))
        );
        assertTrue(error.getMessage().contains("附件不存在或已被提交"), error.getMessage());
    }

    @Test
    @DisplayName("过期暂存附件按状态回收")
    void shouldCleanExpiredTempAttachments() throws IOException {
        AttachDTO uploaded = uploadAttachment();

        // 用未来截止时间触发回收，避免直接改库伪造创建时间
        attachService.cleanTransientFiles(Instant.now().plus(1, ChronoUnit.DAYS));

        assertFalse(attachService.exists(uploaded.getId()));
    }
}
