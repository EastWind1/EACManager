package pers.eastwind.billmanager.reimburse.service;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
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
import pers.eastwind.billmanager.attach.model.FileOp;
import pers.eastwind.billmanager.attach.model.FileOpType;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.attach.service.OfficeFileService;
import pers.eastwind.billmanager.attach.util.FileTxUtil;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.reimburse.model.*;
import pers.eastwind.billmanager.reimburse.repository.ReimburseRepository;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报销单服务
 */
@Slf4j
@Service
public class ReimburseService {
    private final ReimburseMapper reimburseMapper;
    private final AttachmentService attachmentService;
    private final ReimburseRepository reimburseRepository;
    private final TransactionTemplate transactionTemplate;
    private final OfficeFileService officeFileService;

    public ReimburseService(ReimburseMapper reimburseMapper1, AttachmentService attachmentService, ReimburseRepository reimburseRepository, TransactionTemplate transactionTemplate, OfficeFileService officeFileService) {
        this.reimburseMapper = reimburseMapper1;
        this.attachmentService = attachmentService;
        this.reimburseRepository = reimburseRepository;
        this.transactionTemplate = transactionTemplate;
        this.officeFileService = officeFileService;
    }

    /**
     * 根据 id 查询
     *
     * @param id ID
     * @return ReimbursementDTO
     */
    public ReimbursementDTO findById(Integer id) {
        if (id == null) {
            throw new BizException("id不能为空");
        }
        Reimbursement bill = reimburseRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        List<Attachment> attachments = attachmentService.getByBill(id, BillType.REIMBURSEMENT);
        return reimburseMapper.toDTO(bill, attachments);
    }

    /**
     * 创建单据
     *
     * @param reimbursementDTO 单据
     * @return 保存后的单据
     */
    @Transactional
    public ReimbursementDTO create(ReimbursementDTO reimbursementDTO) {
        if (reimbursementDTO.getId() != null && reimburseRepository.existsById(reimbursementDTO.getId())) {
            throw new BizException("单据已存在");
        }
        if (reimbursementDTO.getNumber() == null || reimbursementDTO.getNumber().isEmpty()) {
            reimbursementDTO.setNumber("R" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()) + new SecureRandom().nextInt(1000));
        } else {
            if (reimburseRepository.existsByNumber(reimbursementDTO.getNumber())) {
                throw new BizException("单据编号已存在");
            }
        }
        Reimbursement bill = reimburseRepository.save(reimburseMapper.toEntity(reimbursementDTO));
        attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.REIMBURSEMENT, reimbursementDTO.getAttachments());
        return reimburseMapper.toDTO(bill, attachmentService.getByBill(bill.getId(), BillType.REIMBURSEMENT));
    }


    /**
     * 更新单据
     *
     * @param reimbursementDTO 单据
     * @return 更新后的单据
     */
    @Transactional
    public ReimbursementDTO update(ReimbursementDTO reimbursementDTO) {
        if (reimbursementDTO.getId() == null) {
            throw new BizException("id不能为空");
        }
        Reimbursement bill = reimburseRepository.findById(reimbursementDTO.getId()).orElse(null);
        if (bill == null) {
            throw new BizException("单据不存在");
        }
        reimburseMapper.updateEntityFromDTO(reimbursementDTO, bill);
        bill = reimburseRepository.save(reimburseMapper.toEntity(reimbursementDTO));
        attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.REIMBURSEMENT, reimbursementDTO.getAttachments());
        return reimburseMapper.toDTO(bill, attachmentService.getByBill(bill.getId(), BillType.REIMBURSEMENT));
    }

    /**
     * 根据条件查询
     *
     * @param param 查询参数
     * @return 分页结果
     */
    public Page<ReimbursementDTO> findByParam(ReimburseQueryParam param) {
        if (param == null) {
            throw new BizException("查询参数为空");
        }
        Specification<Reimbursement> specification = (root, query, cb) -> {
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
        // 默认取前 20 行，创建时间降序排序
        int pageIndex = param.getPageIndex() == null ? 0 : param.getPageIndex();
        int pageSize = param.getPageSize() == null ? 20 : param.getPageSize();
        List<Sort.Order> orders = param.getSorts() == null ? List.of(Sort.Order.desc("reimburseDate")) : param.getSorts().stream().map(sortParam -> Sort.Order.by(sortParam.getField()).with(Sort.Direction.fromString(sortParam.getDirection()))).toList();
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));
        Page<Reimbursement> pageResult = reimburseRepository.findAll(specification, pageable);
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
                Reimbursement bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.CREATED) {
                    throw new BizException("非创建状态不能删除");
                }
                reimburseRepository.deleteById(id);
                attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.REIMBURSEMENT, new ArrayList<>());
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
                Reimbursement bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.CREATED) {
                    throw new BizException("非创建状态不能提交");
                }
                bill.setState(ReimburseState.PROCESSING);
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
                Reimbursement bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new BizException("单据不存在");
                }
                if (bill.getState() != ReimburseState.PROCESSING) {
                    throw new BizException("非处理状态不能完成");
                }
                bill.setState(ReimburseState.FINISHED);
                reimburseRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 导出单据
     *
     * @param ids 单据列表
     * @return 压缩文件路径
     */
    public Path export(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        List<Reimbursement> reimbursements = reimburseRepository.findAllById(ids);
        if (reimbursements.isEmpty()) {
            throw new BizException("id不存在");
        }
        // 临时目录
        Path tempPath = attachmentService.createTempDir("export");
        // 文件操作
        List<FileOp> ops = new ArrayList<>();

        // 遍历生成 excel行，并拷贝附件
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("单据编号", "摘要", "总额", "报销日期", "备注"));
        BigDecimal totalAmount = BigDecimal.ZERO;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Reimbursement reimbursement : reimbursements) {
            rows.add(List.of(
                    reimbursement.getNumber(),
                    reimbursement.getSummary(),
                    reimbursement.getTotalAmount().toString(),
                    reimbursement.getReimburseDate() == null ? "" : dateTimeFormatter.format(reimbursement.getReimburseDate().atZone(ZoneId.systemDefault())),
                    reimbursement.getDetails().stream().map((detail) ->
                                    detail.getName() + " : " + detail.getAmount().stripTrailingZeros().toPlainString() + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(reimbursement.getTotalAmount());
            // 创建当前单据附件文件夹
            Path curDir = tempPath.resolve(reimbursement.getNumber());
            // 拷贝当前单据所有附件
            List<Attachment> attachments = attachmentService.getByBill(reimbursement.getId(), BillType.REIMBURSEMENT);
            for (Attachment attachment : attachments) {
                Path origin = attachmentService.getRootPath().resolve(attachment.getRelativePath());
                Path target = curDir.resolve(attachment.getName());
                // 处理可能的重名
                int repeatCount = 1;
                while (Files.exists(target)) {
                    target = curDir.resolve(repeatCount + "-" + attachment.getName());
                    repeatCount++;
                }
                ops.add(new FileOp(FileOpType.COPY, origin, target));
            }
        }
        // 表合计
        rows.add(List.of("", "合计", totalAmount.toString(), "", ""));

        Path excel = tempPath.resolve("导出结果.xlsx");
        officeFileService.generateExcelFromList(rows, excel);
        // 执行文件拷贝
        FileTxUtil.exec(ops);
        // 生成压缩包
        Path zip = attachmentService.getTempPath().resolve(tempPath + ".zip");
        FileUtil.zip(tempPath, zip);
        return zip;
    }
}
