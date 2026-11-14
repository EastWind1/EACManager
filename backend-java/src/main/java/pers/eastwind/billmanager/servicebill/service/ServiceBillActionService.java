package pers.eastwind.billmanager.servicebill.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.util.Snowflake;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.service.CompanyService;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.util.AuthUtil;
import pers.eastwind.billmanager.servicebill.model.*;
import pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillAssigneeRepository;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillRepository;
import pers.eastwind.billmanager.user.service.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class ServiceBillActionService {

    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillAssigneeRepository assigneeRepository;
    private final ServiceBillMapper serviceBillMapper;
    private final ServiceBillAttachService serviceBillAttachService;
    private final TransactionTemplate transactionTemplate;
    private final CompanyService companyService;
    private final UserService userService;
    private final ServiceBillQueryService queryService;

    public ServiceBillActionService(
            ServiceBillRepository serviceBillRepository,
            ServiceBillAssigneeRepository assigneeRepository,
            ServiceBillMapper serviceBillMapper,
            ServiceBillAttachService serviceBillAttachService,
            TransactionTemplate transactionTemplate,
            CompanyService companyService,
            UserService userService,
            ServiceBillQueryService queryService
    ) {
        this.serviceBillRepository = serviceBillRepository;
        this.assigneeRepository = assigneeRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.serviceBillAttachService = serviceBillAttachService;
        this.transactionTemplate = transactionTemplate;
        this.companyService = companyService;
        this.userService = userService;
        this.queryService = queryService;
    }

    private void validateAmount(ServiceBill serviceBill) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ServiceBillDetail detail : serviceBill.getDetails()) {
            if (
                    detail
                            .getQuantity()
                            .multiply(detail.getUnitPrice())
                            .compareTo(detail.getSubtotal()) !=
                            0
            ) {
                throw new BizException("明细金额有误");
            }
            totalAmount = totalAmount.add(detail.getSubtotal());
        }
        if (totalAmount.compareTo(serviceBill.getTotalAmount()) != 0) {
            throw new BizException("总金额有误");
        }
    }

    private void validNumber(String number) {
        if (number == null || number.isBlank()) {
            throw new BizException("单号为空");
        }
        for (int i = 0; i < number.length(); i++) {
            if (number.charAt(i) == '/' || number.charAt(i) == '\\') {
                throw new BizException("单号不能包含 / \\");
            }
        }
        if ("..".equals(number)) {
            throw new BizException("单号不能为 ..");
        }
        if (serviceBillRepository.existsByNumber(number)) {
            throw new BizException("单据编号已存在");
        }
    }

    private Set<Integer> requestedAssigneeIds(Collection<AssigneeDTO> requested) {
        Set<Integer> ids = new HashSet<>();
        if (requested == null) {
            return ids;
        }
        for (AssigneeDTO assignee : requested) {
            if (assignee != null && assignee.getId() != null) {
                ids.add(assignee.getId());
            }
        }
        return ids;
    }

    private Set<Integer> getCurrentAssignees(Integer billId) {
        Set<Integer> ids = new HashSet<>();
        for (ServiceBillAssignee assignee : assigneeRepository.findByServiceBillId(billId)) {
            ids.add(assignee.getUserId());
        }
        return ids;
    }

    /**
     * 统一的负责人变更入口：比较新旧 id，校验权限和用户状态后整体替换关联行。
     */
    private void updateAssignees(Integer billId, Collection<Integer> oldIds, Collection<Integer> ids) {
        Set<Integer> oldSet = oldIds == null ? Set.of() : new HashSet<>(oldIds);
        Set<Integer> newSet = ids == null ? Set.of() : new HashSet<>(ids);
        if (oldSet.equals(newSet)) {
            return;
        }
        AuthorityUser currentUser = AuthUtil.getCurUser();
        if (currentUser == null) {
            throw new BizException("未登录");
        }
        // 创建/编辑时把单据指派给自己不需要额外指派权限；改派他人或取消指派需要 serviceBill:assign
        if (!newSet.equals(Set.of(currentUser.getId()))
                && !AuthUtil.hasAuthority(ServiceBillPermission.ASSIGN.getCode())) {
            throw new BizException("无权限指派服务单");
        }
        replaceAssignees(billId, newSet);
    }

    /**
     * 已由调用方完成权限校验后，整体替换负责人。
     */
    private void replaceAssignees(Integer billId, Collection<Integer> ids) {
        Set<Integer> newSet = ids == null ? Set.of() : new HashSet<>(ids);
        for (Integer userId : newSet) {
            if (userService.findEnabledById(userId) == null) {
                throw new BizException("指派用户不存在或已禁用");
            }
        }
        assigneeRepository.deleteByServiceBillId(billId);
        if (newSet.isEmpty()) {
            return;
        }
        List<ServiceBillAssignee> rows = new ArrayList<>(newSet.size());
        for (Integer userId : newSet) {
            ServiceBillAssignee assignee = new ServiceBillAssignee();
            assignee.setServiceBillId(billId);
            assignee.setUserId(userId);
            rows.add(assignee);
        }
        assigneeRepository.saveAll(rows);
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    @Transactional
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() != null && serviceBillRepository.existsById(serviceBillDTO.getId())) {
            throw new BizException("单据已存在");
        }
        if (serviceBillDTO.getNumber() == null || serviceBillDTO.getNumber().isEmpty()) {
            serviceBillDTO.setNumber("S" + Snowflake.genIntID());
        } else {
            validNumber(serviceBillDTO.getNumber());
        }
        Set<Integer> assigneeIds = requestedAssigneeIds(serviceBillDTO.getAssignees());
        if (assigneeIds.isEmpty()) {
            AuthorityUser currentUser = AuthUtil.getCurUser();
            if (currentUser == null) {
                throw new BizException("未登录");
            }
            assigneeIds = Set.of(currentUser.getId());
        }
        ServiceBill bill = serviceBillMapper.toEntity(serviceBillDTO);
        for (ServiceBillDetail detail : bill.getDetails()) {
            detail.setServiceBill(bill);
        }
        validateAmount(bill);
        bill.setState(ServiceBillState.CREATED);
        bill = serviceBillRepository.save(bill);
        updateAssignees(bill.getId(), Set.of(), assigneeIds);
        serviceBillAttachService.updateAttachments(
                bill.getId(),
                serviceBillDTO.getAttachments()
        );
        return serviceBillMapper.toDTO(bill, serviceBillAttachService.findAttachments(bill.getId()),
                queryService.findAssigneesByBillId(bill.getId()));
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    @Transactional
    public ServiceBillDTO update(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() == null) {
            throw new BizException("id 不能为空");
        }
        ServiceBill bill = queryService.findEntityById(serviceBillDTO.getId());
        if (!bill.getNumber().equals(serviceBillDTO.getNumber())) {
            validNumber(serviceBillDTO.getNumber());
        }
        // 先处理负责人再修改明细：Mapper 清理明细后任何查询都会触发 auto-flush，
        // 此时新明细还没被持久化，Hibernate 会报 detached entity passed to persist。
        if (serviceBillDTO.getAssignees() != null) {
            Set<Integer> currentAssigneeIds = getCurrentAssignees(bill.getId());
            Set<Integer> requestedAssigneeIds = requestedAssigneeIds(serviceBillDTO.getAssignees());
            if (!currentAssigneeIds.equals(requestedAssigneeIds)) {
                if (bill.getState() != ServiceBillState.CREATED && bill.getState() != ServiceBillState.PROCESSING) {
                    throw new BizException("只有新建或处理中的单据才能指派");
                }
                updateAssignees(bill.getId(), currentAssigneeIds, requestedAssigneeIds);
            }
        }
        var originState = bill.getState();
        serviceBillMapper.updateEntityFromDTO(serviceBillDTO, bill);
        for (ServiceBillDetail detail : bill.getDetails()) {
            detail.setServiceBill(bill);
        }
        bill.setState(originState);
        // 公司关联单独处理, 直接使用 MapStruct 会因为代理对象 ID 变化触发关联变更（即使未设置 Cascade）
        if (
                serviceBillDTO.getProductCompany() != null &&
                        serviceBillDTO.getProductCompany().getId() != null &&
                        (bill.getProductCompany() == null ||
                                !Objects.equals(
                                        bill.getProductCompany().getId(),
                                        serviceBillDTO.getProductCompany().getId()
                                ))
        ) {
            Integer companyId = serviceBillDTO.getProductCompany().getId();
            Company targetCompany = companyService.findEntityById(companyId);
            if (targetCompany == null) {
                throw new BizException("公司不存在");
            }
            bill.setProductCompany(targetCompany);
        } else {
            bill.setProductCompany(null);
        }
        validateAmount(bill);
        bill = serviceBillRepository.save(bill);

        serviceBillAttachService.updateAttachments(bill.getId(), serviceBillDTO.getAttachments());
        return serviceBillMapper.toDTO(bill, serviceBillAttachService.findAttachments(bill.getId()),
                queryService.findAssigneesByBillId(bill.getId()));
    }

    /**
     * 指派/改派负责人；assigneeIds 为空表示取消指派。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> assign(List<Integer> ids, List<Integer> assigneeIds) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        Set<Integer> targetIds = assigneeIds == null ? Set.of() : new HashSet<>(assigneeIds);
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.CREATED && bill.getState() != ServiceBillState.PROCESSING) {
                    throw new BizException("只有新建或处理中的单据才能指派");
                }
                replaceAssignees(id, targetIds);
            });
            return null;
        });
    }


    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new BizException("非创建状态的单据不能删除");
                }
                assigneeRepository.deleteByServiceBillId(id);
                serviceBillRepository.deleteById(id);
                serviceBillAttachService.updateAttachments(id, List.of());
            });
            return null;
        });
    }

    /**
     * 仅 CREATED 可处理，状态推进到 PROCESSING。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new BizException("非创建状态的单据不能处理");
                }
                bill.setState(ServiceBillState.PROCESSING);
                log.info("服务单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 仅 PROCESSING 可完成，状态推进到 PROCESSED 并写入完工时间。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> processed(
            List<Integer> ids,
            Instant processedDate
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        final Instant finalProcessedDate =
                processedDate == null ? Instant.now() : processedDate;
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.PROCESSING) {
                    throw new BizException("非处理中状态的单据不能处理完成");
                }
                bill.setState(ServiceBillState.PROCESSED);
                bill.setProcessedDate(finalProcessedDate);
                log.info("服务单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 仅 PROCESSED 可完成，状态推进到 FINISHED 并写入完成时间。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> finish(
            List<Integer> ids,
            Instant finishedDate
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        final Instant finalFinishedDate =
                finishedDate == null ? Instant.now() : finishedDate;
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.PROCESSED) {
                    throw new BizException("非处理完成状态的单据不能完成");
                }
                bill.setState(ServiceBillState.FINISHED);
                bill.setFinishedDate(finalFinishedDate);
                log.info("服务单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 仅 PROCESSING 可撤销，回退到 CREATED 并清空完工时间。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> cancelProcess(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.PROCESSING) {
                    throw new BizException("非处理中状态的单据不能取消处理");
                }
                bill.setState(ServiceBillState.CREATED);
                bill.setProcessedDate(null);
                log.info("服务单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 仅 PROCESSED 可撤销，回退到 PROCESSING 并清空完工时间。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> cancelProcessed(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.PROCESSED) {
                    throw new BizException("非处理完成状态的单据不能取消处理完成");
                }
                bill.setState(ServiceBillState.PROCESSING);
                bill.setProcessedDate(null);
                log.info("服务单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 仅 FINISHED 可撤销，回退到 PROCESSED 并清空完成时间。
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(value = "serviceBill_statistic", allEntries = true),
            }
    )
    public ActionsResult<Integer, Void> cancelFinish(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(_ -> {
                ServiceBill bill = queryService.findEntityById(id);
                if (bill.getState() != ServiceBillState.FINISHED) {
                    throw new BizException("非完成状态的单据不能取消完成");
                }
                bill.setState(ServiceBillState.PROCESSED);
                bill.setFinishedDate(null);
                log.info("服务单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }
}
