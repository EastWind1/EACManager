package pers.eastwind.billmanager.reimburse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.reimburse.model.ReimburseAttachRelation;
import pers.eastwind.billmanager.reimburse.repository.ReimburseAttachRelRepo;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报销单附件关联业务
 *
 */
@Slf4j
@Service
public class ReimburseAttachRelService {
    /**
     * 报销单附件命名空间
     */
    public static final String ATTACH_NAMESPACE = "REIMBURSEMENT";

    private final ReimburseAttachRelRepo reimburseAttachRelRepo;
    private final AttachmentService attachmentService;

    public ReimburseAttachRelService(
            ReimburseAttachRelRepo reimburseAttachRelRepo,
            AttachmentService attachmentService
    ) {
        this.reimburseAttachRelRepo = reimburseAttachRelRepo;
        this.attachmentService = attachmentService;
    }

    /**
     * 获取报销单附件
     *
     * @param billId 报销单 ID
     * @return 附件
     */
    public List<Attachment> findAttachments(Integer billId) {
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return reimburseAttachRelRepo.findAttachmentsByBillId(billId);
    }

    /**
     * 将报销单附件更新为目标集合
     *
     * @param billId         报销单 ID
     * @param attachmentDTOs 要更新为的附件集合
     */
    public void updateAttachments(Integer billId, List<AttachmentDTO> attachmentDTOs) {
        Set<Integer> currentIds = findAttachments(billId)
                .stream()
                .map(Attachment::getId)
                .collect(Collectors.toSet());
        Set<Integer> targetIds = attachmentDTOs
                .stream()
                .map(AttachmentDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 目标集合中不在当前关联里的即为新增，其余为待删除
        Set<Integer> addedIds = new HashSet<>(targetIds);
        addedIds.removeAll(currentIds);
        Set<Integer> removedIds = new HashSet<>(currentIds);
        removedIds.removeAll(targetIds);

        // 先提交新增附件，再断开关联、删除附件与文件
        if (!addedIds.isEmpty()) {
            attachmentService.persist(addedIds);
            addedIds.forEach(attachId -> {
                ReimburseAttachRelation relation = new ReimburseAttachRelation();
                relation.setReimburseId(billId);
                relation.setAttachId(attachId);
                reimburseAttachRelRepo.save(relation);
            });
        }
        if (!removedIds.isEmpty()) {
            reimburseAttachRelRepo.deleteByBillIdAndAttachIds(billId, removedIds);
            attachmentService.delete(removedIds);
        }
    }
}
