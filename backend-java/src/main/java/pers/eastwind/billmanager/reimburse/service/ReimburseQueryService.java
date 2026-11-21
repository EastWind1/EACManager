package pers.eastwind.billmanager.reimburse.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.reimburse.model.Reimburse;
import pers.eastwind.billmanager.reimburse.model.ReimburseDTO;
import pers.eastwind.billmanager.reimburse.model.ReimburseMapper;
import pers.eastwind.billmanager.reimburse.model.ReimburseQueryParam;
import pers.eastwind.billmanager.reimburse.repository.ReimburseRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 报销单查询服务：列表、详情与导出读取；动作服务通过这里按 id 取实体，避免重复维护访问边界。
 */
@Service
public class ReimburseQueryService {
    private final ReimburseRepository reimburseRepository;
    private final ReimburseMapper reimburseMapper;
    private final ReimburseAttachService reimburseAttachService;

    public ReimburseQueryService(
            ReimburseRepository reimburseRepository,
            ReimburseMapper reimburseMapper,
            ReimburseAttachService reimburseAttachService
    ) {
        this.reimburseRepository = reimburseRepository;
        this.reimburseMapper = reimburseMapper;
        this.reimburseAttachService = reimburseAttachService;
    }

    public ReimburseDTO findFullById(Integer id) {
        if (id == null) {
            throw new BizException("id不能为空");
        }
        Reimburse bill = reimburseRepository.findDetailById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        List<Attach> attachments = reimburseAttachService.findAttachments(id);
        return reimburseMapper.toDTO(bill, attachments);
    }

    public Reimburse findEntityById(Integer id) {
        if (id == null) {
            throw new BizException("id不能为空");
        }
        Reimburse bill = reimburseRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        return bill;
    }

    public List<Reimburse> findEntityByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        List<Reimburse> bills = reimburseRepository.findAllById(ids);
        if (bills.isEmpty()) {
            throw new BizException("id不存在");
        }
        return bills;
    }

    public boolean existsByNumber(String number) {
        return reimburseRepository.existsByNumber(number);
    }

    @Transactional(readOnly = true)
    public Page<ReimburseDTO> findByParam(ReimburseQueryParam param) {
        if (param == null) {
            throw new BizException("查询参数为空");
        }
        List<Sort.Order> orders = param.getSorts() == null ? List.of(Sort.Order.desc("reimburseDate"))
                : param.getSorts().stream()
                .map(sortParam -> Sort.Order.by(sortParam.getField())
                        .with(Sort.Direction.fromString(sortParam.getDirection())))
                .toList();
        Pageable pageable = buildPageable(param, Sort.by(orders));

        Specification<Reimburse> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.getNumber() != null) {
                predicates.add(cb.like(root.get("number"), param.getNumber() + "%"));
            }
            if (param.getSummary() != null) {
                predicates.add(cb.like(root.get("summary"), "%" + param.getSummary() + "%"));
            }
            if (param.getStates() != null && !param.getStates().isEmpty()) {
                if (param.getStates().size() == 1) {
                    predicates.add(cb.equal(root.get("state"), param.getStates().getFirst()));
                } else {
                    predicates.add(root.get("state").in(param.getStates()));
                }
            }
            if (param.getReimburseStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reimburseDate"), param.getReimburseStartDate()));
            }
            if (param.getReimburseEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("reimburseDate"), param.getReimburseEndDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Reimburse> pageResult = reimburseRepository.findAll(specification, pageable);
        return pageResult.map(reimburseMapper::toBaseDTO);
    }

    private Pageable buildPageable(ReimburseQueryParam param, Sort sort) {
        if (param.getPageIndex() == null && param.getPageSize() == null) {
            return Pageable.unpaged(sort);
        }
        if (param.getPageIndex() == null || param.getPageSize() == null) {
            throw new BizException("分页参数不能只有一个为空");
        }
        int pageIndex = param.getPageIndex();
        int pageSize = param.getPageSize();
        if (pageIndex < 0) {
            throw new BizException("页码不能小于 0");
        }
        if (pageSize < 1) {
            throw new BizException("分页大小必须大于 0");
        }
        return PageRequest.of(pageIndex, Math.min(pageSize, QueryParam.MAX_PAGE_SIZE), sort);
    }
}
