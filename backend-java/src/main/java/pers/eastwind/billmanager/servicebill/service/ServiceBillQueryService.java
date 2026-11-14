package pers.eastwind.billmanager.servicebill.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.util.AuthUtil;
import pers.eastwind.billmanager.servicebill.model.*;
import pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillAssigneeRepository;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillRepository;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.util.*;

/**
 * 服务单查询服务
 */
@Service
public class ServiceBillQueryService {

    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillAssigneeRepository assigneeRepository;
    private final ServiceBillMapper serviceBillMapper;
    private final ServiceBillAttachService serviceBillAttachService;
    private final UserService userService;

    public ServiceBillQueryService(
            ServiceBillRepository serviceBillRepository,
            ServiceBillAssigneeRepository assigneeRepository,
            ServiceBillMapper serviceBillMapper,
            ServiceBillAttachService serviceBillAttachService,
            UserService userService
    ) {
        this.serviceBillRepository = serviceBillRepository;
        this.assigneeRepository = assigneeRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.serviceBillAttachService = serviceBillAttachService;
        this.userService = userService;
    }

    /**
     * 数据权限用户ID
     * @return 数据权限用户ID，若为 null 表示不限制
     */
    public Integer getScopeUserId() {
        AuthorityUser user = AuthUtil.getCurUser();
        if (user == null) {
            throw new BizException("未登录");
        }
        if (AuthUtil.hasAuthority(ServiceBillPermission.DATA_ALL.getCode())) {
            return null;
        }
        return user.getId();
    }

    /**
     * 判断当前用户是否拥有数据权限
     */
    public void assertCanAccess(Integer billId) {
        if (billId == null) {
            throw new BizException("服务单 ID 为空");
        }
        Integer scopeUserId = getScopeUserId();
        if (scopeUserId == null) {
            return;
        }
        if (!assigneeRepository.existsByServiceBillIdAndUserId(billId, scopeUserId)) {
            throw new BizException("无权访问该服务单");
        }
    }

    /**
     * 根据 ID 查询完整 DTO
     */
    public ServiceBillDTO findFullById(Integer id) {
        if (id == null) {
            throw new BizException("ID不能为空");
        }
        ServiceBill bill = serviceBillRepository.findDetailById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        assertCanAccess(bill.getId());
        return serviceBillMapper.toDTO(bill, serviceBillAttachService.findAttachments(bill.getId()),
                findAssigneesByBillId(bill.getId()));
    }

    /**
     * 获取实体部分，用于动作查询获取
     */
    public ServiceBill findEntityById(Integer id) {
        if (id == null) {
            throw new BizException("ID不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        assertCanAccess(bill.getId());
        return bill;
    }

    @Cacheable(
            value = "serviceBill_query",
            key = "'findByParam_' + @serviceBillQueryService.getScopeUserId() + '_' + #param.hashCode()"
    )
    @Transactional(readOnly = true)
    public Page<ServiceBillDTO> findByParam(ServiceBillQueryParam param) {
        if (param == null) {
            throw new BizException("查询参数为空");
        }
        Specification<ServiceBill> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.getNumber() != null) {
                predicates.add(cb.like(root.get("number"), param.getNumber() + "%"));
            }
            if (param.getStates() != null && !param.getStates().isEmpty()) {
                if (param.getStates().size() == 1) {
                    predicates.add(cb.equal(root.get("state"), param.getStates().getFirst()));
                } else {
                    predicates.add(root.get("state").in(param.getStates()));
                }
            }
            if (param.getProjectName() != null) {
                predicates.add(cb.like(root.get("projectName"), "%" + param.getProjectName() + "%"));
            }
            Integer scopeUserId = getScopeUserId();
            Integer targetAssigneeId = scopeUserId != null ? scopeUserId : param.getAssigneeId();
            if (targetAssigneeId != null) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                var assignee = subquery.from(ServiceBillAssignee.class);
                subquery.select(assignee.get("serviceBillId"))
                        .where(cb.equal(assignee.get("userId"), targetAssigneeId));
                predicates.add(root.get("id").in(subquery));
            }
            if (param.getOrderStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), param.getOrderStartDate()));
            }
            if (param.getOrderEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), param.getOrderEndDate()));
            }
            if (param.getProcessedStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("processedDate"), param.getProcessedStartDate()));
            }
            if (param.getProcessedEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("processedDate"), param.getProcessedEndDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int pageIndex = param.getPageIndex() == null ? 0 : param.getPageIndex();
        int pageSize = param.getPageSize() == null ? 20 : param.getPageSize();
        List<Sort.Order> orders =
                param.getSorts() == null ? List.of(Sort.Order.desc("orderDate"))
                        : param.getSorts().stream().map(sortParam ->
                                Sort.Order.by(sortParam.getField()).with(
                                        Sort.Direction.fromString(
                                                sortParam.getDirection()
                                        )
                                )
                        )
                        .toList();
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));
        Page<ServiceBill> pageResult = serviceBillRepository.findAll(specification, pageable);
        Page<ServiceBillDTO> page = pageResult.map(serviceBillMapper::toBaseDTO);
        fillAssignees(page.getContent());
        return page;
    }

    private void fillAssignees(List<ServiceBillDTO> bills) {
        if (bills == null || bills.isEmpty()) {
            return;
        }
        List<Integer> billIds = bills.stream().map(ServiceBillDTO::getId).toList();
        Map<Integer, List<AssigneeDTO>> assigneesByBill = loadAssignees(billIds);
        for (ServiceBillDTO bill : bills) {
            bill.setAssignees(assigneesByBill.getOrDefault(bill.getId(), List.of()));
        }
    }

    public List<ServiceBill> findEntityByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        List<ServiceBill> bills = serviceBillRepository.findAllById(ids);
        if (bills.isEmpty()) {
            throw new BizException("id不存在");
        }
        for (ServiceBill bill : bills) {
            assertCanAccess(bill.getId());
        }
        return bills;
    }

    /**
     * 指派候选用户列表；只返回启用用户
     */
    public List<AssigneeDTO> findAssignees() {
        return userService.findEnabledUsers().stream()
                .map(user -> new AssigneeDTO(user.getId(), user.getUsername(), user.getName()))
                .toList();
    }

    public List<AssigneeDTO> findAssigneesByBillId(Integer billId) {
        if (billId == null) {
            return List.of();
        }
        return loadAssignees(List.of(billId)).getOrDefault(billId, List.of());
    }

    private Map<Integer, List<AssigneeDTO>> loadAssignees(Collection<Integer> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return Map.of();
        }
        List<ServiceBillAssignee> rows = assigneeRepository.findByServiceBillIdIn(billIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Set<Integer> userIds = new LinkedHashSet<>();
        for (ServiceBillAssignee row : rows) {
            userIds.add(row.getUserId());
        }
        Map<Integer, UserDTO> users = new HashMap<>();
        for (UserDTO user : userService.findByIds(userIds)) {
            users.put(user.getId(), user);
        }
        Map<Integer, List<AssigneeDTO>> result = new HashMap<>();
        for (ServiceBillAssignee row : rows) {
            UserDTO user = users.get(row.getUserId());
            if (user == null) {
                continue;
            }
            result.computeIfAbsent(row.getServiceBillId(), _ -> new ArrayList<>())
                    .add(AssigneeDTO.from(user));
        }
        for (List<AssigneeDTO> assignees : result.values()) {
            assignees.sort(Comparator.comparing(AssigneeDTO::getId));
        }
        return result;
    }
}
