package com.eastwind.EACAfterSaleMgr.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.eastwind.EACAfterSaleMgr.model.common.AttachmentType;
import com.eastwind.EACAfterSaleMgr.model.dto.AttachmentDTO;
import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillQueryParam;
import com.eastwind.EACAfterSaleMgr.model.mapper.ServiceBillMapper;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBill;
import com.eastwind.EACAfterSaleMgr.repository.ServiceBillRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 服务单服务
 */
@Service
public class ServiceBillService {
    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillMapper serviceBillMapper;
    private final OcrService ocrService;
    private final AttachmentService attachmentService;

    public ServiceBillService(ServiceBillRepository serviceBillRepository, ServiceBillMapper serviceBillMapper, OcrService ocrService, AttachmentService attachmentService) {
        this.serviceBillRepository = serviceBillRepository;
        this.serviceBillMapper = serviceBillMapper;
        this.ocrService = ocrService;
        this.attachmentService = attachmentService;
    }

    /**
     * 创建单据
     *
     * @param serviceBillDTO 单据
     * @return 保存后的单据
     */
    @Transactional
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() != null && serviceBillRepository.existsById(serviceBillDTO.getId())) {
            throw new RuntimeException("单据已存在");
        }
        if (serviceBillDTO.getNumber() == null || serviceBillDTO.getNumber().isEmpty()) {
            serviceBillDTO.setNumber(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + new SecureRandom().nextInt(1000));
        } else {
            if (serviceBillRepository.existsByNumber(serviceBillDTO.getNumber())) {
                throw new RuntimeException("单据编号已存在");
            }
        }
        // 若为导入单据，移动临时文件到单据附件目录
        // 在保存之后另起线程执行
        record Move(Path origin, Path target) {
        }
        List<Move> moveThread = new ArrayList<>();

        for (AttachmentDTO attachment : serviceBillDTO.getAttachments()) {
            Path origin = Path.of(attachment.getRelativePath());
            Path target = Path.of(serviceBillDTO.getNumber()).resolve(origin.getFileName());
            attachment.setRelativePath(target.toString());
            moveThread.add(new Move(origin, target));
        }

        ServiceBill bill = serviceBillRepository.save(serviceBillMapper.toServiceBill(serviceBillDTO));
        for (Move move : moveThread) {
            Thread.startVirtualThread(() -> {
                attachmentService.move(move.origin, move.target);
            });
        }
        return serviceBillMapper.toServiceBillDTO(bill);
    }

    /**
     * 根据文件生成单据
     */
    public ServiceBillDTO generateByFile(MultipartFile file) {
        AttachmentDTO attachment = attachmentService.uploadTemp(file);
        if (attachment.getType() == AttachmentType.IMAGE) {
            OcrResult ocrResult = ocrService.runOcr(Path.of(attachment.getRelativePath()));
            if (ocrResult == null) {
                throw new RuntimeException("OCR 结果为空");
            }

            ServiceBillDTO serviceBillDTO = new ServiceBillDTO();
            ocrService.executeMapRule(ocrResult, serviceBillDTO);

            serviceBillDTO.setAttachments(List.of(attachment));
            return serviceBillDTO;
        } else {
            throw new UnsupportedOperationException("暂不支持非图片");
        }
    }

    /**
     * 更新单据
     *
     * @param serviceBillDTO 单据
     * @return 更新后的单据
     */
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
        return serviceBillMapper.toServiceBillDTO(serviceBillRepository.save(bill));
    }

    /**
     * 删除单据
     *
     * @param id 单据 ID
     */
    @Transactional
    public void deleteById(Integer id) {
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        serviceBillRepository.deleteById(id);
    }

    public ServiceBillDTO findById(int id) {
        return serviceBillMapper.toServiceBillDTO(serviceBillRepository.findById(id).orElse(null));
    }

    /**
     * 根据条件查询
     *
     * @param param 查询参数
     * @return 分页结果
     */
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
            if (param.getCreatedStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), param.getCreatedStartDate()));
            }
            if (param.getCreatedEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), param.getCreatedEndDate()));
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
        List<Sort.Order> orders = param.getSorts() == null ? List.of(
                Sort.Order.desc("createdDate")
        ) : param.getSorts().stream().map(sortParam -> Sort.Order.by(sortParam.getField()).with(Sort.Direction.fromString(sortParam.getDirection()))).toList();
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));
        Page<ServiceBill> pageResult = serviceBillRepository.findAll(specification, pageable);
        return pageResult.map(serviceBillMapper::toServiceBillDTO);
    }
}
