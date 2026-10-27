package pers.eastwind.billmanager.servicebill.service;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.util.Snowflake;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.repository.CompanyRepository;
import pers.eastwind.billmanager.servicebill.model.*;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ServiceBillService {

    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillMapper serviceBillMapper;
    private final ServiceBillAttachService serviceBillAttachService;
    private final TransactionTemplate transactionTemplate;
    private final CompanyRepository companyRepository;

    public ServiceBillService(
            ServiceBillRepository serviceBillRepository,
            ServiceBillMapper serviceBillMapper,
            ServiceBillAttachService serviceBillAttachService,
            TransactionTemplate transactionTemplate,
            CompanyRepository companyRepository
    ) {
        this.serviceBillRepository = serviceBillRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.serviceBillAttachService = serviceBillAttachService;
        this.transactionTemplate = transactionTemplate;
        this.companyRepository = companyRepository;
    }

    public ServiceBillDTO findById(Integer id) {
        if (id == null) {
            throw new BizException("ID不能为空");
        }
        ServiceBill bill = serviceBillRepository.findDetailById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        List<Attach> attachments = serviceBillAttachService.findAttachments(bill.getId());
        return serviceBillMapper.toDTO(bill, attachments);
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

    /**
     * 明细是关联维护方（父方 details 为 mappedBy），缺这一步外键会为空。
     */
    private void bindDetails(ServiceBill serviceBill) {
        for (ServiceBillDetail detail : serviceBill.getDetails()) {
            detail.setServiceBill(serviceBill);
        }
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
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
            if (serviceBillRepository.existsByNumber(serviceBillDTO.getNumber())) {
                throw new BizException("单据编号已存在");
            }
        }
        ServiceBill bill = serviceBillMapper.toEntity(serviceBillDTO);
        bindDetails(bill);
        validateAmount(bill);
        bill.setState(ServiceBillState.CREATED);
        bill = serviceBillRepository.save(bill);
        serviceBillAttachService.updateAttachments(
                bill.getId(),
                serviceBillDTO.getAttachments()
        );
        return serviceBillMapper.toDTO(bill, serviceBillAttachService.findAttachments(bill.getId()));
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'sumTotalAmountByMonth'"
                    ),
            }
    )
    @Transactional
    public ServiceBillDTO update(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() == null) {
            throw new BizException("id 不能为空");
        }
        ServiceBill bill = serviceBillRepository
                .findById(serviceBillDTO.getId())
                .orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        var originState = bill.getState();
        serviceBillMapper.updateEntityFromDTO(serviceBillDTO, bill);
        bindDetails(bill);
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
            Company targetCompany = companyRepository
                    .findById(serviceBillDTO.getProductCompany().getId())
                    .orElse(null);
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
        return serviceBillMapper.toDTO(bill, serviceBillAttachService.findAttachments(bill.getId()));
    }

    @Cacheable(
            value = "serviceBill_query",
            key = "'findByParam_' + #param.hashCode()"
    )
    public Page<ServiceBillDTO> findByParam(ServiceBillQueryParam param) {
        if (param == null) {
            throw new BizException("查询参数为空");
        }
        Specification<ServiceBill> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.getNumber() != null) {
                predicates.add(
                        cb.like(root.get("number"), param.getNumber() + "%")
                );
            }
            if (param.getStates() != null && !param.getStates().isEmpty()) {
                if (param.getStates().size() == 1) {
                    predicates.add(
                            cb.equal(
                                    root.get("state"),
                                    param.getStates().getFirst()
                            )
                    );
                } else {
                    predicates.add(root.get("state").in(param.getStates()));
                }
            }
            if (param.getProjectName() != null) {
                predicates.add(
                        cb.like(
                                root.get("projectName"),
                                "%" + param.getProjectName() + "%"
                        )
                );
            }
            if (param.getOrderStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("orderDate"),
                                param.getOrderStartDate()
                        )
                );
            }
            if (param.getOrderEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("orderDate"),
                                param.getOrderEndDate()
                        )
                );
            }
            if (param.getProcessedStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("processedDate"),
                                param.getProcessedStartDate()
                        )
                );
            }
            if (param.getProcessedEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("processedDate"),
                                param.getProcessedEndDate()
                        )
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int pageIndex = param.getPageIndex() == null ? 0 : param.getPageIndex();
        int pageSize = param.getPageSize() == null ? 20 : param.getPageSize();
        List<Sort.Order> orders =
                param.getSorts() == null
                        ? List.of(Sort.Order.desc("orderDate"))
                        : param
                        .getSorts()
                        .stream()
                        .map(sortParam ->
                                Sort.Order.by(sortParam.getField()).with(
                                        Sort.Direction.fromString(
                                                sortParam.getDirection()
                                        )
                                )
                        )
                        .toList();
        Pageable pageable = PageRequest.of(
                pageIndex,
                pageSize,
                Sort.by(orders)
        );
        Page<ServiceBill> pageResult = serviceBillRepository.findAll(
                specification,
                pageable
        );
        return pageResult.map(serviceBillMapper::toBaseDTO);
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "serviceBill_query", allEntries = true),
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
            }
    )
    public ActionsResult<Integer, Void> delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new BizException("非创建状态的单据不能删除");
                }
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
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
            }
    )
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
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
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'sumReceiveAmountByMonth'"
                    ),
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
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
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
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
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
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
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
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
            }
    )
    public ActionsResult<Integer, Void> cancelProcess(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
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
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
            }
    )
    public ActionsResult<Integer, Void> cancelProcessed(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
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
                    @CacheEvict(
                            value = "serviceBill_statistic",
                            key = "'countBillsByState'"
                    ),
            }
    )
    public ActionsResult<Integer, Void> cancelFinish(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository
                        .findById(id)
                        .orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
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
