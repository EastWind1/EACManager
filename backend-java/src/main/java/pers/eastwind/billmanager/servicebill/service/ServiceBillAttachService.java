package pers.eastwind.billmanager.servicebill.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.servicebill.model.ServiceBillAttach;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillAttachRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务单附件关联业务
 *
 * <p>服务单与附件的关联由本模块自行维护；附件的落库、提交与删除交给附件模块，
 * 差异对比只是「目标集合」与「本单据当前关联」的集合运算。</p>
 */
@Slf4j
@Service
public class ServiceBillAttachService {
    /**
     * 服务单附件的命名空间
     *
     * <p>只用于隔离附件存储，附件模块不解释其含义；文件上传接口由前端传入同一个值。</p>
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

    /**
     * 获取服务单附件
     *
     * @param billId 服务单 ID
     * @return 附件
     */
    public List<Attach> findAttachments(Integer billId) {
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return serviceBillAttachRepository.findAttachmentsByBillId(billId);
    }

    /**
     * 将服务单附件更新为目标集合
     *
     * @param billId         服务单 ID
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
        Set<Integer> addedIds = new LinkedHashSet<>(targetIds);
        addedIds.removeAll(currentIds);
        Set<Integer> removedIds = new LinkedHashSet<>(currentIds);
        removedIds.removeAll(targetIds);

        // 先提交新增附件，再断开关联、删除附件与文件
        if (!addedIds.isEmpty()) {
            attachService.persist(addedIds);
            addedIds.forEach(attachId -> {
                ServiceBillAttach relation = new ServiceBillAttach();
                relation.setServiceBillId(billId);
                relation.setAttachId(attachId);
                serviceBillAttachRepository.save(relation);
            });
        }
        if (!removedIds.isEmpty()) {
            serviceBillAttachRepository.deleteByBillIdAndAttachIds(billId, removedIds);
            attachService.delete(removedIds);
        }
    }
}
