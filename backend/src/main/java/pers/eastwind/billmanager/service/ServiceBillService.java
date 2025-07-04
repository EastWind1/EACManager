package pers.eastwind.billmanager.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import pers.eastwind.billmanager.model.common.AttachmentType;
import pers.eastwind.billmanager.model.common.ServiceBillState;
import pers.eastwind.billmanager.model.dto.ActionsResult;
import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.dto.ServiceBillDTO;
import pers.eastwind.billmanager.model.dto.ServiceBillQueryParam;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.mapper.AttachmentMapper;
import pers.eastwind.billmanager.model.mapper.ServiceBillMapper;
import pers.eastwind.billmanager.model.entity.ServiceBill;
import pers.eastwind.billmanager.repository.AttachmentRepository;
import pers.eastwind.billmanager.repository.ServiceBillRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务单服务
 */
@Slf4j
@Service
public class ServiceBillService {
    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillMapper serviceBillMapper;
    private final OcrService ocrService;
    private final AttachmentService attachmentService;
    private final TransactionTemplate transactionTemplate;
    private final AttachmentMapper attachmentMapper;
    private final AttachmentRepository attachmentRepository;
    private final OfficeFileService officeFileService;

    public ServiceBillService(ServiceBillRepository serviceBillRepository, ServiceBillMapper serviceBillMapper, OcrService ocrService, AttachmentService attachmentService, TransactionTemplate transactionTemplate, AttachmentMapper attachmentMapper, AttachmentRepository attachmentRepository, OfficeFileService officeFileService) {
        this.serviceBillRepository = serviceBillRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.ocrService = ocrService;
        this.attachmentService = attachmentService;
        this.transactionTemplate = transactionTemplate;
        this.attachmentMapper = attachmentMapper;
        this.attachmentRepository = attachmentRepository;
        this.officeFileService = officeFileService;
    }

    /**
     * 根据id查询
     * @param id ID
     * @return ServiceBillDTO
     */
    public ServiceBillDTO findById(int id) {
        return serviceBillMapper.toServiceBillDTO(serviceBillRepository.findById(id).orElse(null));
    }
    /**
     * 获取移动临时文件线程,
     * 用于保存时处理临时文件
     *
     * @param dto 单据
     * @return Runnable 列表
     */
    private List<Runnable> getMoveTempRunnable(ServiceBillDTO dto) {
        List<Runnable> moveRunnable = new ArrayList<>();
        if (dto.getAttachments() == null || dto.getAttachments().isEmpty()) {
            return moveRunnable;
        }
        for (AttachmentDTO attachment : dto.getAttachments()) {
            Path origin = attachmentService.getAbsolutePath(Path.of(attachment.getRelativePath()));
            if (attachmentService.isTempFile(origin)) {
                Path targetRelativePath = Path.of(dto.getNumber()).resolve(origin.getFileName());
                Path target = attachmentService.getAbsolutePath(targetRelativePath);
                attachment.setRelativePath(targetRelativePath.toString());
                moveRunnable.add(() -> attachmentService.move(origin, target));
            }
        }
        return moveRunnable;
    }

    /**
     * 创建单据
     *
     * @param serviceBillDTO 单据
     * @return 保存后的单据
     */
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    @Transactional
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() != null && serviceBillRepository.existsById(serviceBillDTO.getId())) {
            throw new RuntimeException("单据已存在");
        }
        if (serviceBillDTO.getNumber() == null || serviceBillDTO.getNumber().isEmpty()) {
            serviceBillDTO.setNumber(DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()) + new SecureRandom().nextInt(1000));
        } else {
            if (serviceBillRepository.existsByNumber(serviceBillDTO.getNumber())) {
                throw new RuntimeException("单据编号已存在");
            }
        }
        // 移动临时文件
        List<Runnable> moves = getMoveTempRunnable(serviceBillDTO);
        ServiceBill bill = serviceBillRepository.save(serviceBillMapper.toServiceBill(serviceBillDTO));
        for (Runnable move : moves) {
            Thread.startVirtualThread(move);
        }
        return serviceBillMapper.toServiceBillDTO(bill);
    }

    /**
     * 根据文件生成单据
     *
     * @param file 文件
     * @return 单据
     */
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    public ServiceBillDTO generateByFile(MultipartFile file) {
        AttachmentDTO attachment = attachmentService.uploadTemp(file);
        ServiceBillDTO serviceBillDTO = new ServiceBillDTO();
        Path absolutePath = attachmentService.getAbsolutePath(Path.of(attachment.getRelativePath()));
        if (attachment.getType() == AttachmentType.PDF && attachmentService.isScannedPdf(absolutePath)) {
            Path tempImage = attachmentService.renderPDFToImage(absolutePath);
            ocrService.parseImage(tempImage, serviceBillDTO);
        } else if (attachment.getType() == AttachmentType.IMAGE) {
            ocrService.parseImage(absolutePath, serviceBillDTO);
        } else if (attachment.getType() == AttachmentType.EXCEL) {
            officeFileService.parseExcel(absolutePath, serviceBillDTO);
        } else {
            throw new RuntimeException("不支持的文件类型");
        }
        serviceBillDTO.setAttachments(List.of(attachment));
        return serviceBillDTO;
    }

    /**
     * 更新单据
     *
     * @param serviceBillDTO 单据
     * @return 更新后的单据
     */
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    @Transactional
    public ServiceBillDTO update(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() == null) {
            throw new RuntimeException("id不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(serviceBillDTO.getId()).orElse(null);
        if (bill == null) {
            throw new RuntimeException("单据不存在");
        }
        serviceBillMapper.updateEntityFromDTO(serviceBillDTO, bill);
        // 移动临时文件
        List<Runnable> moves = getMoveTempRunnable(serviceBillDTO);
        ServiceBill savedBill = serviceBillRepository.save(serviceBillMapper.toServiceBill(serviceBillDTO));
        for (Runnable move : moves) {
            Thread.startVirtualThread(move);
        }
        return serviceBillMapper.toServiceBillDTO(savedBill);
    }

    /**
     * 根据条件查询
     *
     * @param param 查询参数
     * @return 分页结果
     */
    @Cacheable(value = "serviceBill", key = "'findByParam' + #param")
    public Page<ServiceBillDTO> findByParam(ServiceBillQueryParam param) {
        if (param == null) {
            throw new RuntimeException("查询参数为空");
        }
        Specification<ServiceBill> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.getNumber() != null) {
                predicates.add(cb.like(root.get("number"), param.getNumber() + "%"));
            }
            if (param.getState() != null && !param.getState().isEmpty()) {
                if (param.getState().size() == 1) {
                    predicates.add(cb.equal(root.get("state"), param.getState().getFirst()));
                } else {
                    predicates.add(root.get("state").in(param.getState()));
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
        List<Sort.Order> orders = param.getSorts() == null ? List.of(Sort.Order.desc("createdDate")) : param.getSorts().stream().map(sortParam -> Sort.Order.by(sortParam.getField()).with(Sort.Direction.fromString(sortParam.getDirection()))).toList();
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));
        Page<ServiceBill> pageResult = serviceBillRepository.findAll(specification, pageable);
        return pageResult.map(serviceBillMapper::toBasicServiceBillDTO);
    }

    /**
     * 批量删除单据
     *
     * @param ids 单据 ID 列表
     * @return 批量操作结果
     */
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    public ActionsResult<Integer, Void> delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new RuntimeException("非创建状态的单据不能删除");
                }
                serviceBillRepository.deleteById(id);
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
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    public ActionsResult<Integer, Void> process(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.CREATED) {
                    throw new RuntimeException("非创建状态的单据不能处理");
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
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    public ActionsResult<Integer, Void> processed(List<Integer> ids, Instant processedDate) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("id不能为空");
        }
        final Instant finalProcessedDate = processedDate == null ? Instant.now() : processedDate;
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.PROCESSING) {
                    throw new RuntimeException("非处理中状态的单据不能处理完成");
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
    @CacheEvict(value = {"statistic", "serviceBill"}, allEntries = true)
    public ActionsResult<Integer, Void> finish(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("id不能为空");
        }
        return ActionsResult.executeActions(ids, id -> {
            transactionTemplate.executeWithoutResult(status -> {
                ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
                if (bill == null) {
                    throw new RuntimeException("单据不存在");
                }
                if (bill.getState() != ServiceBillState.PROCESSED) {
                    throw new RuntimeException("非处理完成状态的单据不能完成");
                }
                bill.setState(ServiceBillState.FINISHED);
                serviceBillRepository.save(bill);
            });
            return null;
        });
    }

    /**
     * 单独添加附件
     */
    public AttachmentDTO addAttachment(Integer id, MultipartFile file) {
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new RuntimeException("单据不存在");
        }
        AttachmentDTO attachment = attachmentService.upload(file, attachmentService.getAbsolutePath(Path.of(bill.getNumber())));
        return transactionTemplate.execute((status) -> {
            Attachment newAttachment = new Attachment();
            attachmentMapper.updateEntityFromDTO(attachment, newAttachment);
            newAttachment.setServiceBill(bill);
            attachmentRepository.save(newAttachment);
            return attachmentMapper.toAttachmentDTO(attachmentRepository.save(newAttachment));
        });
    }

    /**
     * 导出单据
     * @param ids 单据列表
     * @return 压缩文件路径
     */
    public Path export(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("id不能为空");
        }
        List<ServiceBill> serviceBills = serviceBillRepository.findAllById(ids);
        if (serviceBills.isEmpty()) {
            throw new RuntimeException("id不存在");
        }
        // 创建临时目录
        String dirName = "导出-" + System.currentTimeMillis();
        Path tempDir = attachmentService.getTempPath().resolve(dirName);
        tempDir = attachmentService.createDirectory(tempDir);
        // 遍历生成 excel行，并拷贝附件
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("单据编号", "状态", "项目名称", "项目地址", "总额", "备注"));
        BigDecimal totalAmount  = BigDecimal.ZERO;
        for (ServiceBill serviceBill : serviceBills) {
            rows.add(List.of(
                    serviceBill.getNumber(),
                    serviceBill.getState().getLabel(),
                    serviceBill.getProjectName(),
                    serviceBill.getProjectAddress(),
                    serviceBill.getTotalAmount().toString(),
                    serviceBill.getDetails().stream().map((detail) ->
                            detail.getDevice() + ": " + detail.getQuantity().stripTrailingZeros().toPlainString() + "; ").collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(serviceBill.getTotalAmount());
            // 复制附件文件夹
            Path origin = attachmentService.getAbsolutePath(Path.of(serviceBill.getNumber()));
            attachmentService.copy(origin, tempDir, true);
        }
        // 表合计
        rows.add(List.of("", "", "", "合计", totalAmount.toString(), ""));

        Path excel = tempDir.resolve("导出结果.xlsx");
        excel = attachmentService.createFile(excel);
        officeFileService.generateExcelFromList(rows, excel);
        // 压缩
        Path zip = attachmentService.getTempPath().resolve(dirName+".zip");
        attachmentService.createFile(zip);

        attachmentService.zip(tempDir, zip);
        return zip;
    }
}
