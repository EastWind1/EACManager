package pers.eastwind.billmanager.reimburse.service;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.util.Snowflake;
import pers.eastwind.billmanager.reimburse.model.*;
import pers.eastwind.billmanager.reimburse.repository.ReimburseRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 报销单服务
 */
@Slf4j
@Service
public class ReimburseService {
    private final ReimburseMapper reimburseMapper;
    private final ReimburseAttachService reimburseAttachService;
    private final ReimburseRepository reimburseRepository;
    private final TransactionTemplate transactionTemplate;

    public ReimburseService(ReimburseMapper reimburseMapper1, ReimburseAttachService reimburseAttachService, ReimburseRepository reimburseRepository, TransactionTemplate transactionTemplate) {
        this.reimburseMapper = reimburseMapper1;
        this.reimburseAttachService = reimburseAttachService;
        this.reimburseRepository = reimburseRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 根据 id 查询
     *
     * @param id ID
     * @return ReimburseDTO
     */
    public ReimburseDTO findById(Integer id) {
        if (id == null) {
            throw new BizException("id不能为空");
        }
        Reimburse bill = reimburseRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        List<Attach> attachments = reimburseAttachService.findAttachments(id);
        return reimburseMapper.toDTO(bill, attachments);
    }

    /**
     * 创建单据
     *
     * @param reimburseDTO 单据
     * @return 保存后的单据
     */
    @Transactional
    public ReimburseDTO create(ReimburseDTO reimburseDTO) {
        if (reimburseDTO.getId() != null) {
            reimburseDTO.setId(null);
        }
        if (reimburseDTO.getNumber() == null || reimburseDTO.getNumber().isEmpty()) {
            reimburseDTO.setNumber("R" + Snowflake.genIntID());
        } else {
            if (reimburseRepository.existsByNumber(reimburseDTO.getNumber())) {
                throw new BizException("单据编号已存在");
            }
        }
        reimburseDTO.setState(ReimburseState.CREATED);
        Reimburse bill = reimburseRepository.save(reimburseMapper.toEntity(reimburseDTO));
        reimburseAttachService.updateAttachments(bill.getId(), reimburseDTO.getAttachments());
        return reimburseMapper.toDTO(bill, reimburseAttachService.findAttachments(bill.getId()));
    }


    /**
     * 更新单据
     *
     * @param reimburseDTO 单据
     * @return 更新后的单据
     */
    @Transactional
    public ReimburseDTO update(ReimburseDTO reimburseDTO) {
        if (reimburseDTO.getId() == null) {
            throw new BizException("id不能为空");
        }
        Reimburse bill = reimburseRepository.findById(reimburseDTO.getId()).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        var originState = bill.getState();
        reimburseMapper.updateEntityFromDTO(reimburseDTO, bill);
        bill.setState(originState);
        bill = reimburseRepository.save(bill);
        reimburseAttachService.updateAttachments(bill.getId(), reimburseDTO.getAttachments());
        return reimburseMapper.toDTO(bill, reimburseAttachService.findAttachments(bill.getId()));
    }

    /**
     * 根据条件查询
     *
     * @param param 查询参数
     * @return 分页结果
     */
    public Page<ReimburseDTO> findByParam(ReimburseQueryParam param) {
        if (param == null) {
            throw new BizException("查询参数为空");
        }
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
        Page<Reimburse> pageResult = reimburseRepository.findAll(specification, param.getPageable());
        return pageResult.map(reimburseMapper::toBaseDTO);
    }

    /**
     * 批量删除单据
     *
     * @param ids 单据 ID 列表
     * @return 批量操作结果
     */
    public ActionsResult<Integer, Void> delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.CREATED) {
                    throw new BizException("非创建状态不能删除");
                }
                reimburseRepository.deleteById(id);
                reimburseAttachService.updateAttachments(bill.getId(), List.of());
            });
            return null;
        });
    }

    /**
     * 提交处理
     */
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.CREATED) {
                    throw new BizException("非创建状态不能提交");
                }
                bill.setState(ReimburseState.PROCESSING);
                log.info("报销单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                reimburseRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 完成
     */
    public ActionsResult<Integer, Void> finish(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.PROCESSING) {
                    throw new BizException("非处理状态不能完成");
                }
                bill.setState(ReimburseState.FINISHED);
                log.info("报销单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                reimburseRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 取消处理状态
     */
    public ActionsResult<Integer, Void> cancelProcess(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.PROCESSING) {
                    throw new BizException("非处理状态不能取消处理");
                }
                bill.setState(ReimburseState.CREATED);
                log.info("报销单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                reimburseRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 取消完成状态
     */
    public ActionsResult<Integer, Void> cancelFinish(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.FINISHED) {
                    throw new BizException("非完成状态不能取消");
                }
                bill.setState(ReimburseState.PROCESSING);
                log.info("报销单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                reimburseRepository.save(bill);
            });
            return null;
        });
    }

}
