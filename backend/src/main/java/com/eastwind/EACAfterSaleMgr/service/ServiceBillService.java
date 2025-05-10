package com.eastwind.EACAfterSaleMgr.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.eastwind.EACAfterSaleMgr.model.dto.AttachmentDTO;
import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.model.mapper.ServiceBillMapper;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBill;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBillProcessorDetail;
import com.eastwind.EACAfterSaleMgr.model.common.ServiceBillState;
import com.eastwind.EACAfterSaleMgr.model.entity.User;
import com.eastwind.EACAfterSaleMgr.repository.ServiceBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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
            serviceBillDTO.setNumber(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + new SecureRandom().nextInt(1000));
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
        AttachmentDTO path = attachmentService.uploadTemp(file);
        OcrResult ocrResult = ocrService.runOcr(path);
        if (ocrResult == null) {
            throw new RuntimeException("OCR 结果为空");
        }
        ServiceBillDTO serviceBillDTO = new ServiceBillDTO();
        ocrService.executeMapRule(ocrResult, serviceBillDTO);
        AttachmentDTO attach = new AttachmentDTO();
        attach.setName(path.getFileName().toString());
        // 禁止向外部传递绝对路径
        attach.setRelativePath(path.toString());
        serviceBillDTO.setAttachments(List.of(attach));
        return serviceBillDTO;
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

    public List<ServiceBillDTO> findAll() {
        return serviceBillMapper.toServiceBillDTOs(serviceBillRepository.findAll());
    }

    public List<ServiceBillDTO> findAllByStateAndProcessor(ServiceBillState state, User user) {
        return serviceBillMapper.toServiceBillDTOs(serviceBillRepository.findAllByStateAndProcessor(state, user));
    }

    @Transactional
    public void allocateProcessor(Integer id, List<User> users) {
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new RuntimeException("未找到该单据");
        }

        if (bill.getState() != ServiceBillState.CREATED && bill.getState() != ServiceBillState.PROCESSING) {
            throw new RuntimeException("该单据非新建或处理中，无法分配");
        }

        for (ServiceBillProcessorDetail processDetail : bill.getProcessDetails()) {
            for (User user : users) {
                if (Objects.equals(processDetail.getProcessUser().getId(), user.getId())) {
                    throw new RuntimeException("[" + user.getName() + "]正在处理该单据，无需再次分配");
                }
            }
        }
        for (User user : users) {
            ServiceBillProcessorDetail processorDetail = new ServiceBillProcessorDetail();
            processorDetail.setProcessUser(user);
            processorDetail.setAcceptDate(LocalDateTime.now());
            bill.getProcessDetails().add(processorDetail);
        }
        if (bill.getState() == ServiceBillState.CREATED) {
            bill.setState(ServiceBillState.PROCESSING);
        }
        serviceBillRepository.save(bill);
    }
}
