package pers.eastwind.billmanager.reimburse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.util.Snowflake;
import pers.eastwind.billmanager.reimburse.model.Reimburse;
import pers.eastwind.billmanager.reimburse.model.ReimburseDTO;
import pers.eastwind.billmanager.reimburse.model.ReimburseMapper;
import pers.eastwind.billmanager.reimburse.model.ReimburseState;
import pers.eastwind.billmanager.reimburse.repository.ReimburseRepository;

import java.util.List;

/**
 * 报销单动作服务：只做事务内写操作，按 id 读写复用查询服务的访问入口。
 */
@Slf4j
@Service
public class ReimburseActionService {
    private final ReimburseMapper reimburseMapper;
    private final ReimburseAttachService reimburseAttachService;
    private final ReimburseRepository reimburseRepository;
    private final ReimburseQueryService queryService;
    private final TransactionTemplate transactionTemplate;

    public ReimburseActionService(
            ReimburseMapper reimburseMapper,
            ReimburseAttachService reimburseAttachService,
            ReimburseRepository reimburseRepository,
            ReimburseQueryService queryService,
            TransactionTemplate transactionTemplate
    ) {
        this.reimburseMapper = reimburseMapper;
        this.reimburseAttachService = reimburseAttachService;
        this.reimburseRepository = reimburseRepository;
        this.queryService = queryService;
        this.transactionTemplate = transactionTemplate;
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
        if (queryService.existsByNumber(number)) {
            throw new BizException("单据编号已存在");
        }
    }

    @Transactional
    public ReimburseDTO create(ReimburseDTO reimburseDTO) {
        if (reimburseDTO.getId() != null) {
            reimburseDTO.setId(null);
        }
        if (reimburseDTO.getNumber() == null || reimburseDTO.getNumber().isEmpty()) {
            reimburseDTO.setNumber("R" + Snowflake.genIntID());
        } else {
            validNumber(reimburseDTO.getNumber());
        }
        reimburseDTO.setState(ReimburseState.CREATED);
        Reimburse bill = reimburseMapper.toEntity(reimburseDTO);
        bindDetails(bill);
        bill = reimburseRepository.save(bill);
        reimburseAttachService.updateAttachments(bill.getId(), reimburseDTO.getAttachments());
        return reimburseMapper.toDTO(bill, reimburseAttachService.findAttachments(bill.getId()));
    }

    /**
     * 明细是关联维护方（父方 details 为 mappedBy），缺这一步外键会为空。
     */
    private void bindDetails(Reimburse reimburse) {
        for (var detail : reimburse.getDetails()) {
            detail.setReimburse(reimburse);
        }
    }

    @Transactional
    public ReimburseDTO update(ReimburseDTO reimburseDTO) {
        if (reimburseDTO.getId() == null) {
            throw new BizException("id不能为空");
        }
        Reimburse bill = queryService.findEntityById(reimburseDTO.getId());
        ReimburseState originState = bill.getState();
        if (!bill.getNumber().equals(reimburseDTO.getNumber())) {
            validNumber(reimburseDTO.getNumber());
        }
        reimburseMapper.updateEntityFromDTO(reimburseDTO, bill);
        bindDetails(bill);
        bill.setState(originState);
        bill = reimburseRepository.save(bill);
        reimburseAttachService.updateAttachments(bill.getId(), reimburseDTO.getAttachments());
        return reimburseMapper.toDTO(bill, reimburseAttachService.findAttachments(bill.getId()));
    }

    public ActionsResult<Integer, Void> delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = queryService.findEntityById(id);
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
     * 仅 CREATED 可提交，状态推进到 PROCESSING。
     */
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        return transit(ids, ReimburseState.CREATED, ReimburseState.PROCESSING,
                "非创建状态不能提交");
    }

    /**
     * 仅 PROCESSING 可完成，状态推进到 FINISHED。
     */
    public ActionsResult<Integer, Void> finish(List<Integer> ids) {
        return transit(ids, ReimburseState.PROCESSING, ReimburseState.FINISHED,
                "非处理状态不能完成");
    }

    /**
     * 仅 PROCESSING 可撤销，回退到 CREATED。
     */
    public ActionsResult<Integer, Void> cancelProcess(List<Integer> ids) {
        return transit(ids, ReimburseState.PROCESSING, ReimburseState.CREATED,
                "非处理状态不能取消处理");
    }

    /**
     * 仅 FINISHED 可撤销，回退到 PROCESSING。
     */
    public ActionsResult<Integer, Void> cancelFinish(List<Integer> ids) {
        return transit(ids, ReimburseState.FINISHED, ReimburseState.PROCESSING,
                "非完成状态不能取消");
    }

    /**
     * 每次只前进一步/后退一步，并要求精确的前置状态；逐条事务，便于返回部分成功。
     */
    private ActionsResult<Integer, Void> transit(
            List<Integer> ids,
            ReimburseState from,
            ReimburseState to,
            String errorMessage
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimburse bill = queryService.findEntityById(id);
                if (bill.getState() != from) {
                    throw new BizException(errorMessage);
                }
                bill.setState(to);
                log.info("报销单 {} 状态变更为 {}", bill.getNumber(), bill.getState());
                reimburseRepository.save(bill);
            });
            return null;
        });
    }
}
