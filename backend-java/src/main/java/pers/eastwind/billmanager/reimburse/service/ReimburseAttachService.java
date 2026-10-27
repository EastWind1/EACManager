package pers.eastwind.billmanager.reimburse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.reimburse.model.ReimburseAttach;
import pers.eastwind.billmanager.reimburse.repository.ReimburseAttachRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReimburseAttachService {
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

    public List<Attach> findAttachments(Integer billId) {
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return reimburseAttachRepository.findAttachmentsByBillId(billId);
    }

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

        if (addedIds.isEmpty() && removedIds.isEmpty()) {
            return;
        }
        // 先提交新增附件，再断开关联、删除附件与文件
        if (!addedIds.isEmpty()) {
            attachService.persist(addedIds);
            List<ReimburseAttach> relations = new ArrayList<>(addedIds.size());
            addedIds.forEach(attachId -> {
                ReimburseAttach relation = new ReimburseAttach();
                relation.setReimburseId(billId);
                relation.setAttachId(attachId);
                relations.add(relation);
            });
            reimburseAttachRepository.saveAll(relations);
        }
        if (!removedIds.isEmpty()) {
            reimburseAttachRepository.deleteByBillIdAndAttachIds(billId, removedIds);
            attachService.delete(removedIds);
        }
    }
}
