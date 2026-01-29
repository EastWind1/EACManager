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
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.attach.model.BillType;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.repository.CompanyRepository;
import pers.eastwind.billmanager.servicebill.model.*;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 服务单业务
 */
@Slf4j
@Service
public class ServiceBillBizService {
    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillMapper serviceBillMapper;
    private final AttachmentService attachmentService;
    private final TransactionTemplate transactionTemplate;
    private final CompanyRepository companyRepository;

    public ServiceBillBizService(ServiceBillRepository serviceBillRepository, ServiceBillMapper serviceBillMapper, AttachmentService attachmentService, TransactionTemplate transactionTemplate, CompanyRepository companyRepository) {
        this.serviceBillRepository = serviceBillRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.attachmentService = attachmentService;
        this.transactionTemplate = transactionTemplate;
        this.companyRepository = companyRepository;
    }

    /**
     * 根据id查询
     *
     * @param id ID
     * @return ServiceBillDTO
     */
    public ServiceBillDTO findById(Integer id) {
        if (id == null) {
            throw new BizException("ID不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        List<Attachment> attachments = attachmentService.getByBill(bill.getId(), BillType.SERVICE_BILL);
        return serviceBillMapper.toDTO(bill, attachments);
    }

    /**
     * 校验金额
     */
    private void validateAmount(ServiceBill serviceBill) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ServiceBillDetail detail : serviceBill.getDetails()) {
            if (detail.getQuantity().multiply(detail.getUnitPrice()).compareTo(detail.getSubtotal()) != 0) {
                throw new BizException("明细金额有误");
            }
            totalAmount = totalAmount.add(detail.getSubtotal());
        }
        if (totalAmount.compareTo(serviceBill.getTotalAmount()) != 0) {
            throw new BizException("总金额有误");
        }
    }
    /**
     * 创建单据
     *
     * @param serviceBillDTO 单据
     * @return 保存后的单据
     */
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'")
    })
    @Transactional
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() != null && serviceBillRepository.existsById(serviceBillDTO.getId())) {
            throw new BizException("单据已存在");
        }
        if (serviceBillDTO.getNumber() == null || serviceBillDTO.getNumber().isEmpty()) {
            serviceBillDTO.setNumber("S" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()) + new SecureRandom().nextInt(1000));
        } else {
            if (serviceBillRepository.existsByNumber(serviceBillDTO.getNumber())) {
                throw new BizException("单据编号已存在");
            }
        }
        ServiceBill bill = serviceBillMapper.toEntity(serviceBillDTO);
        validateAmount(bill);
        bill = serviceBillRepository.save(bill);
        attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.SERVICE_BILL, serviceBillDTO.getAttachments());
        return serviceBillMapper.toDTO(bill, attachmentService.getByBill(bill.getId(), BillType.SERVICE_BILL));
    }



    /**
     * 更新单据
     *
     * @param serviceBillDTO 单据
     * @return 更新后的单据
     */
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'sumTotalAmountByMonth'")
    })
    @Transactional
    public ServiceBillDTO update(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() == null) {
            throw new BizException("id 不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(serviceBillDTO.getId()).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        serviceBillMapper.updateEntityFromDTO(serviceBillDTO, bill);
        // 公司关联单独处理, 直接使用 MapStruct 会因为代理对象 ID 变化触发关联变更（即使未设置 Cascade）
        if (serviceBillDTO.getProductCompany() != null
                && serviceBillDTO.getProductCompany().getId() != null
                && (bill.getProductCompany() == null || !Objects.equals(bill.getProductCompany().getId(), serviceBillDTO.getProductCompany().getId()))) {
            Company targetCompany = companyRepository.findById(serviceBillDTO.getProductCompany().getId()).orElse(null);
            if (targetCompany == null) {
                throw new BizException("公司不存在");
            }
            bill.setProductCompany(targetCompany);
        } else {
            bill.setProductCompany(null);
        }
        validateAmount(bill);
        bill = serviceBillRepository.save(bill);

        attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.SERVICE_BILL, serviceBillDTO.getAttachments());

        return serviceBillMapper.toDTO(bill, attachmentService.getByBill(bill.getId(), BillType.SERVICE_BILL));
    }


    /**
     * 根据条件查询
     *
     * @param param 查询参数
     * @return 分页结果
     */
    @Cacheable(value = "serviceBill_query", key = "'findByParam_' + #param.hashCode()")
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
        // 默认取前 20 行，创建时间降序排序
        int pageIndex = param.getPageIndex() == null ? 0 : param.getPageIndex();
        int pageSize = param.getPageSize() == null ? 20 : param.getPageSize();
        List<Sort.Order> orders = param.getSorts() == null ? List.of(Sort.Order.desc("orderDate")) :
                param.getSorts().stream().map(sortParam -> Sort.Order.by(sortParam.getField())
                        .with(Sort.Direction.fromString(sortParam.getDirection()))).toList();
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));
        Page<ServiceBill> pageResult = serviceBillRepository.findAll(specification, pageable);
        return pageResult.map(serviceBillMapper::toBaseDTO);
    }

    /**
     * 批量删除单据
     *
     * @param ids 单据 ID 列表
     * @return 批量操作结果
     */
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'")
    })
    public ActionsResult<Integer, Void> delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new BizException("非创建状态的单据不能删除");
                }
                serviceBillRepository.deleteById(id);
                attachmentService.updateRelativeAttach(id, bill.getNumber(), BillType.SERVICE_BILL, new ArrayList<>());
            });
            return null;
        });
    }

    /**
     * 批量更改为处理中
     *
     * @param ids 单据 ID 列表
     * @return 批量操作结果
     */
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'")
    })
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new BizException("非创建状态的单据不能处理");
                }
                bill.setState(ServiceBillState.PROCESSING);
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 批量更改为处理完成
     *
     * @param ids           单据 ID
     * @param processedDate 完成日期，默认为当前时间
     */
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'"),
            @CacheEvict(value = "serviceBill_statistic", key = "'sumReceiveAmountByMonth'")
    })
    public ActionsResult<Integer, Void> processed(List<Integer> ids, Instant processedDate) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        final Instant finalProcessedDate = processedDate == null ? Instant.now() : processedDate;
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.PROCESSING) {
                    throw new BizException("非处理中状态的单据不能处理完成");
                }
                bill.setState(ServiceBillState.PROCESSED);
                bill.setProcessedDate(processedDate);
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 批量更改为完成
     */
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'")
    })
    public ActionsResult<Integer, Void> finish(List<Integer> ids, Instant finishedDate) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        final Instant finalFinishedDate = finishedDate == null ? Instant.now() : finishedDate;
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.PROCESSED) {
                    throw new BizException("非处理完成状态的单据不能完成");
                }
                bill.setState(ServiceBillState.FINISHED);
                bill.setFinishedDate(finalFinishedDate);
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

}
