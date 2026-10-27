package pers.eastwind.billmanager.servicebill.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.servicebill.model.ServiceBillAttach;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillAttachRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 附件落库、提交与删除交给附件模块；本模块只维护关联，差异对比是目标集合与当前关联的集合运算。
 */
@Slf4j
@Service
public class ServiceBillAttachService {
    /**
     * 仅用于隔离附件存储，附件模块不解释其含义；前端上传时须传同一个值。
     */
    public static final String ATTACH_NAMESPACE = "SERVICE_BILL";

    private final ServiceBillAttachRepository serviceBillAttachRepository;
    private final AttachService attachService;

    public ServiceBillAttachService(
            ServiceBillAttachRepository serviceBillAttachRepository,
            AttachService attachService
    ) {
        this.serviceBillAttachRepository = serviceBillAttachRepository;
        this.attachService = attachService;
    }

    public List<Attach> findAttachments(Integer billId) {
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return serviceBillAttachRepository.findAttachmentsByBillId(billId);
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
        Set<Integer> addedIds = new LinkedHashSet<>(targetIds);
        addedIds.removeAll(currentIds);
        Set<Integer> removedIds = new LinkedHashSet<>(currentIds);
        removedIds.removeAll(targetIds);

        if (addedIds.isEmpty() && removedIds.isEmpty()) {
            return;
        }
        // 先提交新增附件，再断开关联、删除附件与文件
        if (!addedIds.isEmpty()) {
            attachService.persist(addedIds);
            List<ServiceBillAttach> relations = new ArrayList<>(addedIds.size());
            addedIds.forEach(attachId -> {
                ServiceBillAttach relation = new ServiceBillAttach();
                relation.setServiceBillId(billId);
                relation.setAttachId(attachId);
                relations.add(relation);
            });
            serviceBillAttachRepository.saveAll(relations);
        }
        if (!removedIds.isEmpty()) {
            serviceBillAttachRepository.deleteByBillIdAndAttachIds(billId, removedIds);
            attachService.delete(removedIds);
        }
    }
}
