package pers.eastwind.billmanager.attach.service;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.attach.config.AttachConfigProperties;
import pers.eastwind.billmanager.attach.model.*;
import pers.eastwind.billmanager.attach.repository.AttachmentRepository;
import pers.eastwind.billmanager.attach.repository.BillAttachRelationRepository;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.exception.BizException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 附件服务
 */
@Slf4j
@Service
public class AttachmentService implements InitializingBean {
    private final AttachConfigProperties properties;
    private final AttachmentRepository attachmentRepository;
    private final BillAttachRelationRepository billAttachRelationRepository;
    private final AttachmentMapper attachmentMapper;
    private final TransactionTemplate transactionTemplate;
    /**
     * 根目录
     */
    @Getter
    private Path rootPath;
    /**
     * 临时目录
     */
    @Getter
    private Path tempPath;

    public AttachmentService(AttachConfigProperties properties, AttachmentRepository attachmentRepository, BillAttachRelationRepository billAttachRelationRepository, AttachmentMapper attachmentMapper, TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.attachmentRepository = attachmentRepository;
        this.billAttachRelationRepository = billAttachRelationRepository;
        this.attachmentMapper = attachmentMapper;
        this.transactionTemplate = transactionTemplate;
    }


    /**
     * 初始化附件目录
     */
    @Override
    public void afterPropertiesSet() {
        rootPath = properties.getPath().normalize().toAbsolutePath();
        String TEMP_DIR = properties.getTemp();
        this.tempPath = rootPath.resolve(TEMP_DIR).normalize().toAbsolutePath();
        if (rootPath.startsWith(this.tempPath)) {
            throw new BizException("附件目录不能在临时目录内");
        }

        FileUtil.createDirectories(rootPath);
        FileUtil.createDirectories(tempPath);
    }

    /**
     * 获取绝对路径
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    public Path getAbsolutePath(Path relativePath) {
        if (relativePath == null) {
            throw new BizException("路径不能为空");
        }
        // 去除头部的斜杠
        if (relativePath.startsWith("/")) {
            String pathStr = relativePath.toString().substring(1);
            relativePath = Path.of(pathStr);
        }
        return rootPath.resolve(relativePath).normalize().toAbsolutePath();
    }

    /**
     * 校验路径
     * 禁止使用根路径外的路径
     */
    private void validPath(Path path) {
        if (path == null) {
            throw new BizException("路径为空");
        }
        if (!path.startsWith(rootPath)) {
            throw new BizException("禁止使用外部路径");
        }
    }

    /**
     * 判断是否是临时文件
     */
    public boolean isTempFile(Path path) {
        return path.startsWith(tempPath);
    }
    /**
     * 上传临时文件
     */
    public List<AttachmentDTO> uploadTemps(List<Resource> resources) {
        List<AttachmentDTO> res = new ArrayList<>();
        for (Resource resource : resources) {
            String realFileName = String.format("T%d-%s", System.currentTimeMillis(), resource.getFilename());
            Path targetPath = tempPath.resolve(realFileName);
            UploadResult file = FileUtil.upload(resource, targetPath);
            AttachmentDTO attachment = new AttachmentDTO();
            attachment.setName(file.filename());
            attachment.setType(file.type());
            attachment.setRelativePath(rootPath.relativize(file.path()).toString());
            res.add(attachment);
        }
        return res;
    }
    /**
     * 根据 ID 获取附件
     */
    public AttachmentDTO getById(Integer id) {
        return attachmentMapper.toDTO(attachmentRepository.findById(id).orElseThrow(() -> new BizException("附件不存在")));
    }
    /**
     * 获取业务单据附件
     *
     * @param billId   单据 ID
     * @param billType 单据类型
     * @return 业务单据附件
     */
    public List<Attachment> getByBill(Integer billId, BillType billType) {
        if (billType == null) {
            throw new BizException("单据类型不能为空");
        }
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return attachmentRepository.findByBill(billId, billType);
    }

    /**
     * 根据目标附件集合更新业务单据关联附件
     *
     * @param billId         单据 ID
     * @param billNumber     单据编号
     * @param billType       单据类型
     * @param attachmentDTOs 目标附件集合
     * @return 更新后的附件集合
     */
    @Transactional
    public List<Attachment> updateRelativeAttach(Integer billId, String billNumber, BillType billType, List<AttachmentDTO> attachmentDTOs) {
        List<BillAttachRelation> billAttachRelations = billAttachRelationRepository.findByBillIdAndBillType(billId, billType);
        Set<Integer> removeIds = new HashSet<>();
        for (BillAttachRelation billAttachRelation : billAttachRelations) {
            removeIds.add(billAttachRelation.getAttachId());
        }

        for (AttachmentDTO attachmentDTO : attachmentDTOs) {
            // 新增
            if (attachmentDTO.getId() == null) {
                Attachment newAttachment = attachmentMapper.toEntity(attachmentDTO);
                // 移动临时文件
                Path origin = getAbsolutePath(Path.of(newAttachment.getRelativePath()));
                if (isTempFile(origin)) {
                    Path realFileName = tempPath.relativize(origin);
                    Path targetPath = rootPath.resolve(Path.of(billNumber).resolve(realFileName));
                    newAttachment.setRelativePath(rootPath.resolve(targetPath).toString());
                    try {
                        PathUtils.createParentDirectories(targetPath);
                        Files.move(origin, targetPath);
                    } catch (IOException e) {
                        throw new BizException("移动文件失败",e);
                    }
                }
                // 设置业务单据关联关系
                newAttachment = attachmentRepository.save(newAttachment);
                BillAttachRelation billAttachRelation = new BillAttachRelation();
                billAttachRelation.setBillId(billId);
                billAttachRelation.setBillType(billType);
                billAttachRelation.setAttachId(newAttachment.getId());
                billAttachRelationRepository.save(billAttachRelation);
            } else {
                // 未变化
                removeIds.remove(attachmentDTO.getId());
            }
        }
        // 删除
        for (BillAttachRelation billAttachRelation : billAttachRelations) {
            if (removeIds.contains(billAttachRelation.getAttachId())) {
                billAttachRelationRepository.delete(billAttachRelation);
            }
        }

        return attachmentRepository.findByBill(billId, billType);
    }

    /**
     * 删除没有业务关联的附件
     */
    private void cleanUnattach() {
        List<Attachment> attachments = attachmentRepository.findByBillIsNull();
        for (Attachment attachment : attachments) {
            try {
                Files.delete(getAbsolutePath(Path.of(attachment.getRelativePath())));
            } catch (IOException e) {
                throw new BizException("删除文件失败",e);
            }
            transactionTemplate.executeWithoutResult(status -> {
                attachmentRepository.delete(attachment);
            });
        }
    }

    /**
     * 清理文件
     */
    @PreDestroy
    public void cleanTemp() {
        // 删除临时文件夹
        try {
            PathUtils.deleteDirectory(tempPath);
        } catch (IOException e) {
            throw new BizException("删除临时文件夹失败",e);
        }
        // 删除没有业务关联的附件
        cleanUnattach();
    }
}
