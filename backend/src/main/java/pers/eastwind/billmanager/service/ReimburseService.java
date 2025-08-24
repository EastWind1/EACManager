package pers.eastwind.billmanager.service;

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
import pers.eastwind.billmanager.model.common.ReimburseState;
import pers.eastwind.billmanager.model.dto.ActionsResult;
import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.dto.ReimburseQueryParam;
import pers.eastwind.billmanager.model.dto.ReimbursementDTO;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.entity.Reimbursement;
import pers.eastwind.billmanager.model.mapper.ReimburseMapper;
import pers.eastwind.billmanager.repository.ReimburseRepository;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            throw new RuntimeException("id不能为空");
        }
        return reimburseMapper.toDTO(reimburseRepository.findById(id).orElse(null));
    }

    /**
     * 处理附件
     *
     * @param origins 原始附件集合
     * @param dto     传入 DTO
     * @return db操作后，移动或删除文件的动作
     */
    private List<Runnable> processAttach(List<Attachment> origins, ReimbursementDTO dto) {
        Set<Integer> targets = new HashSet<>();
        List<Runnable> actions = new ArrayList<>();

        for (AttachmentDTO attachment : dto.getAttachments()) {
            if (attachment.getId() != null) {
                targets.add(attachment.getId());
            }
            Path origin = attachmentService.getAbsolutePath(Path.of(attachment.getRelativePath()));
            if (attachmentService.isTempFile(origin)) {
                Path targetDirRelativePath = Path.of(dto.getNumber());
                Path target = attachmentService.getAbsolutePath(targetDirRelativePath);
                attachment.setRelativePath(targetDirRelativePath.resolve(origin.getFileName()).toString());
                actions.add(() -> attachmentService.move(origin, target));
            }
        }

        for (Attachment origin : origins) {
            if (!targets.contains(origin.getId())) {
                actions.add(() -> attachmentService.delete(attachmentService.getAbsolutePath(Path.of(origin.getRelativePath()))));
            }
        }

        return actions;
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
            throw new RuntimeException("单据已存在");
        }
        if (reimbursementDTO.getNumber() == null || reimbursementDTO.getNumber().isEmpty()) {
            reimbursementDTO.setNumber("R" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()) + new SecureRandom().nextInt(1000));
        } else {
            if (reimburseRepository.existsByNumber(reimbursementDTO.getNumber())) {
                throw new RuntimeException("单据编号已存在");
            }
        }
        // 移动临时文件
        List<Runnable> moves = processAttach(new ArrayList<>(), reimbursementDTO);
        Reimbursement bill = reimburseRepository.save(reimburseMapper.toEntity(reimbursementDTO));
        for (Runnable move : moves) {
            Thread.startVirtualThread(move);
        }
        return reimburseMapper.toDTO(bill);
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
            throw new RuntimeException("id不能为空");
        }
        Reimbursement bill = reimburseRepository.findById(reimbursementDTO.getId()).orElse(null);
        if (bill == null) {
            throw new RuntimeException("单据不存在");
        }
        reimburseMapper.updateEntityFromDTO(reimbursementDTO, bill);
        List<Runnable> moves = processAttach(bill.getAttachments(), reimbursementDTO);
        Reimbursement savedBill = reimburseRepository.save(reimburseMapper.toEntity(reimbursementDTO));
        for (Runnable move : moves) {
            Thread.startVirtualThread(move);
        }
        return reimburseMapper.toDTO(savedBill);
    }

    /**
     * 根据条件查询
     *
     * @param param 查询参数
     * @return 分页结果
     */
    public Page<ReimbursementDTO> findByParam(ReimburseQueryParam param) {
        if (param == null) {
            throw new RuntimeException("查询参数为空");
        }
        Specification<Reimbursement> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.getNumber() != null) {
                predicates.add(cb.like(root.get("number"), param.getNumber() + "%"));
            }
            if (param.getSummary() != null) {
                predicates.add(cb.like(root.get("summary"), "%" + param.getSummary() + "%"));
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
            throw new RuntimeException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimbursement bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ReimburseState.CREATED) {
                    throw new RuntimeException("非创建状态不能删除");
                }
                reimburseRepository.deleteById(id);
            });
            return null;
        });
    }

    /**
     * 提交处理
     */
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimbursement bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ReimburseState.CREATED) {
                    throw new RuntimeException("非创建状态不能提交");
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
            throw new RuntimeException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                Reimbursement bill = reimburseRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ReimburseState.PROCESSING) {
                    throw new RuntimeException("非处理状态不能完成");
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
            throw new RuntimeException("id不能为空");
        }
        List<Reimbursement> reimbursements = reimburseRepository.findAllById(ids);
        if (reimbursements.isEmpty()) {
            throw new RuntimeException("id不存在");
        }
        // 创建临时目录
        String dirName = "导出-" + System.currentTimeMillis();
        Path tempDir = attachmentService.getTempPath().resolve(dirName);
        tempDir = attachmentService.createDirectory(tempDir);
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
                    dateTimeFormatter.format(reimbursement.getReimburseDate().atZone(ZoneId.systemDefault())),
                    reimbursement.getDetails().stream().map((detail) ->
                                    detail.getName() + " : " + detail.getAmount().stripTrailingZeros().toPlainString() + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(reimbursement.getTotalAmount());
            // 复制附件文件夹
            Path origin = attachmentService.getAbsolutePath(Path.of(reimbursement.getNumber()));
            if (Files.exists(origin)) {
                attachmentService.copy(origin, tempDir, true);
            }
        }
        // 表合计
        rows.add(List.of("", "合计", totalAmount.toString(), "", ""));

        Path excel = tempDir.resolve("导出结果.xlsx");
        excel = attachmentService.createFile(excel);
        officeFileService.generateExcelFromList(rows, excel);
        // 压缩
        Path zip = attachmentService.getTempPath().resolve(dirName + ".zip");
        attachmentService.zip(tempDir, zip);
        return zip;
    }
}
