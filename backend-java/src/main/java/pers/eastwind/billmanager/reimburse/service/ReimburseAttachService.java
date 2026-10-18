package pers.eastwind.billmanager.reimburse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.reimburse.model.ReimburseAttach;
import pers.eastwind.billmanager.reimburse.repository.ReimburseAttachRepository;

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
public class ReimburseAttachService {
    /**
     * 报销单附件命名空间
     */
    public static final String ATTACH_NAMESPACE = "REIMBURSEMENT";

    private final ReimburseAttachRepository reimburseAttachRepository;
    private final AttachService attachService;

    public ReimburseAttachService(
            ReimburseAttachRepository reimburseAttachRepository,
            AttachService attachService
    ) {
        this.reimburseAttachRepository = reimburseAttachRepository;
        this.attachService = attachService;
    }

    /**
     * 获取报销单附件
     *
     * @param billId 报销单 ID
     * @return 附件
     */
    public List<Attach> findAttachments(Integer billId) {
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return reimburseAttachRepository.findAttachmentsByBillId(billId);
    }

    /**
     * 将报销单附件更新为目标集合
     *
     * @param billId         报销单 ID
     * @param attachDTOs 要更新为的附件集合
     */
    public void updateAttachments(Integer billId, List<AttachDTO> attachDTOs) {
        Set<Integer> currentIds = findAttachments(billId)
                .stream()
                .map(Attach::getId)
                .collect(Collectors.toSet());
        Set<Integer> targetIds = attachDTOs
                .stream()
                .map(AttachDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 目标集合中不在当前关联里的即为新增，其余为待删除
        Set<Integer> addedIds = new HashSet<>(targetIds);
        addedIds.removeAll(currentIds);
        Set<Integer> removedIds = new HashSet<>(currentIds);
        removedIds.removeAll(targetIds);

        // 先提交新增附件，再断开关联、删除附件与文件
        if (!addedIds.isEmpty()) {
            attachService.persist(addedIds);
            addedIds.forEach(attachId -> {
                ReimburseAttach relation = new ReimburseAttach();
                relation.setReimburseId(billId);
                relation.setAttachId(attachId);
                reimburseAttachRepository.save(relation);
            });
        }
        if (!removedIds.isEmpty()) {
            reimburseAttachRepository.deleteByBillIdAndAttachIds(billId, removedIds);
            attachService.delete(removedIds);
        }
    }
}
