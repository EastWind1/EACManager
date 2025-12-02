package pers.eastwind.billmanager.service;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.model.common.AttachmentType;
import pers.eastwind.billmanager.model.common.BillType;
import pers.eastwind.billmanager.model.common.ServiceBillState;
import pers.eastwind.billmanager.model.dto.ActionsResult;
import pers.eastwind.billmanager.model.dto.MonthSumAmount;
import pers.eastwind.billmanager.model.dto.ServiceBillDTO;
import pers.eastwind.billmanager.model.dto.ServiceBillQueryParam;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.entity.ServiceBill;
import pers.eastwind.billmanager.model.mapper.AttachmentMapper;
import pers.eastwind.billmanager.model.mapper.ServiceBillMapper;
import pers.eastwind.billmanager.repository.ServiceBillRepository;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
    private final AttachmentMapper attachmentMapper;
    private final TransactionTemplate transactionTemplate;
    private final OfficeFileService officeFileService;

    public ServiceBillService(ServiceBillRepository serviceBillRepository, ServiceBillMapper serviceBillMapper, OcrService ocrService, AttachmentService attachmentService, AttachmentMapper attachmentMapper, TransactionTemplate transactionTemplate, OfficeFileService officeFileService) {
        this.serviceBillRepository = serviceBillRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.ocrService = ocrService;
        this.attachmentService = attachmentService;
        this.attachmentMapper = attachmentMapper;
        this.transactionTemplate = transactionTemplate;
        this.officeFileService = officeFileService;
    }

    /**
     * 根据id查询
     *
     * @param id ID
     * @return ServiceBillDTO
     */
    public ServiceBillDTO findById(Integer id) {
        if (id == null) {
            throw new RuntimeException("ID不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new RuntimeException("单据不存在");
        }
        List<Attachment> attachments = attachmentService.getByBill(bill.getId(), BillType.SERVICE_BILL);
        return serviceBillMapper.toDTO(bill, attachments);
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
            throw new RuntimeException("单据已存在");
        }
        if (serviceBillDTO.getNumber() == null || serviceBillDTO.getNumber().isEmpty()) {
            serviceBillDTO.setNumber("S" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()) + new SecureRandom().nextInt(1000));
        } else {
            if (serviceBillRepository.existsByNumber(serviceBillDTO.getNumber())) {
                throw new RuntimeException("单据编号已存在");
            }
        }
        ServiceBill bill = serviceBillRepository.save(serviceBillMapper.toEntity(serviceBillDTO));
        List<Attachment> attachments = attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.SERVICE_BILL, serviceBillDTO.getAttachments());
        return serviceBillMapper.toDTO(bill, attachments);
    }

    /**
     * 根据文件生成单据
     *
     * @param resource    文件资源
     * @return 单据
     */
    public ServiceBillDTO generateByFile(Resource resource) {
        Attachment attachment = attachmentService.uploadTemp(List.of(resource)).getFirst();
        ServiceBillDTO serviceBillDTO = new ServiceBillDTO();
        Path absolutePath = attachmentService.getAbsolutePath(Path.of(attachment.getRelativePath()));
        if (attachment.getType() == AttachmentType.PDF) {
            Path tempImage = attachmentService.renderPDFToImage(absolutePath);
            ocrService.parseImage(tempImage, serviceBillDTO);
        } else if (attachment.getType() == AttachmentType.IMAGE) {
            ocrService.parseImage(absolutePath, serviceBillDTO);
        } else if (attachment.getType() == AttachmentType.EXCEL) {
            officeFileService.parseExcel(absolutePath, serviceBillDTO);
        } else {
            throw new RuntimeException("不支持的文件类型");
        }
        serviceBillDTO.setAttachments(List.of(attachmentMapper.toDTO(attachment)));
        return serviceBillDTO;
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
            throw new RuntimeException("id不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(serviceBillDTO.getId()).orElse(null);
        if (bill == null) {
            throw new RuntimeException("单据不存在");
        }
        serviceBillMapper.updateEntityFromDTO(serviceBillDTO, bill);
        bill = serviceBillRepository.save(bill);

        List<Attachment> attachments = attachmentService.updateRelativeAttach(bill.getId(), bill.getNumber(), BillType.SERVICE_BILL, serviceBillDTO.getAttachments());

        return serviceBillMapper.toDTO(bill, attachments);
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
            throw new RuntimeException("查询参数为空");
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
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'"),
            @CacheEvict(value = "serviceBill_statistic", key = "'sumReceiveAmountByMonth'")
    })
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
    @Caching(evict = {
            @CacheEvict(value = "serviceBill_query", allEntries = true),
            @CacheEvict(value = "serviceBill_statistic", key = "'countBillsByState'")
    })
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
     * 导出单据
     *
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
        rows.add(List.of("单据编号", "状态", "项目名称", "项目地址", "总额", "安装完成日期", "备注"));
        BigDecimal totalAmount = BigDecimal.ZERO;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (ServiceBill serviceBill : serviceBills) {
            rows.add(List.of(
                    serviceBill.getNumber(),
                    serviceBill.getState().getLabel(),
                    serviceBill.getProjectName(),
                    serviceBill.getProjectAddress(),
                    serviceBill.getTotalAmount().toString(),
                    dateTimeFormatter.format(serviceBill.getProcessedDate().atZone(ZoneId.systemDefault())),
                    serviceBill.getDetails().stream().map((detail) ->
                                    detail.getDevice() + " : " + detail.getUnitPrice().stripTrailingZeros().toPlainString()
                                            + " * " + detail.getQuantity().stripTrailingZeros().toPlainString() + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(serviceBill.getTotalAmount());
            // 复制附件文件夹
            Path origin = attachmentService.getAbsolutePath(Path.of(serviceBill.getNumber()));
            if (Files.exists(origin)) {
                attachmentService.copy(origin, tempDir, true);
            }
        }
        // 表合计
        rows.add(List.of("", "", "", "合计", totalAmount.toString(), ""));

        Path excel = tempDir.resolve("导出结果.xlsx");
        excel = attachmentService.createFile(excel);
        officeFileService.generateExcelFromList(rows, excel);
        // 压缩
        Path zip = attachmentService.getTempPath().resolve(dirName + ".zip");

        attachmentService.zip(tempDir, zip);
        return zip;
    }

    /**
     * 统计不同状态的服务单据数量
     *
     * @return 包含各状态数量的 Map
     */
    @Cacheable(value = "serviceBill_statistic", key = "'countBillsByState'")
    public Map<ServiceBillState, Long> countBillsByState() {
        List<Object[]> results = serviceBillRepository.countByState();
        Map<ServiceBillState, Long> stateCountMap = new HashMap<>();

        for (Object[] result : results) {
            ServiceBillState state = (ServiceBillState) result[0];
            Long count = (Long) result[1];
            stateCountMap.put(state, count);
        }

        return stateCountMap;
    }


    /**
     * 按月份统计应收和已收服务单据金额总和
     *
     * @return 每个月份与对应金额的 Map
     */
    @Cacheable(value = "serviceBill_statistic", key = "'sumReceiveAmountByMonth'")
    public List<MonthSumAmount> sumReceiveAmountByMonth() {

        Instant preYear = Instant.now().minus(365, ChronoUnit.DAYS);
        List<Object[]> results = serviceBillRepository.sumAmountByStateGroupByMonth(
                List.of(ServiceBillState.PROCESSED, ServiceBillState.FINISHED),
                preYear, Instant.now());
        List<MonthSumAmount> rows = new ArrayList<>();

        for (Object[] result : results) {
            int rowYear = ((Number) result[0]).intValue();
            int month = ((Number) result[1]).intValue();
            BigDecimal totalAmount = new BigDecimal(result[2].toString());

            rows.add(new MonthSumAmount(YearMonth.of(rowYear, month).toString(), totalAmount));
        }
        rows.sort(Comparator.comparing(MonthSumAmount::month));
        return rows;
    }
}
