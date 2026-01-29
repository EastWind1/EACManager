package pers.eastwind.billmanager.attach.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.attach.config.AttachConfigProperties;
import pers.eastwind.billmanager.attach.model.*;
import pers.eastwind.billmanager.attach.repository.AttachmentRepository;
import pers.eastwind.billmanager.attach.repository.BillAttachRelationRepository;
import pers.eastwind.billmanager.attach.util.FileTxUtil;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.exception.FileOpException;

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
    /**
     * 根目录
     */
    @Getter
    private Path rootPath;
    /**
     * 应用临时目录
     */
    @Getter
    private Path tempPath;
    private static final String TEMP_PREFIX = "eac-";
    public AttachmentService(AttachConfigProperties properties, AttachmentRepository attachmentRepository, BillAttachRelationRepository billAttachRelationRepository, AttachmentMapper attachmentMapper) {
        this.properties = properties;
        this.attachmentRepository = attachmentRepository;
        this.billAttachRelationRepository = billAttachRelationRepository;
        this.attachmentMapper = attachmentMapper;
    }

    /**
     * 初始化附件目录
     */
    @Override
    public void afterPropertiesSet() {
        rootPath = properties.getPath().normalize().toAbsolutePath();
        try {
            Files.createDirectories(rootPath);
            tempPath = Files.createTempDirectory(TEMP_PREFIX);
            tempPath.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new FileOpException("创建附件目录失败", e);
        }
    }
    /**
     * 创建临时文件
     * @param prefix 前缀
     * @param suffix 后缀, 为空时默认为 .tmp
     */
    public Path createTempFile(String prefix, String suffix) {
        try {
            Path res = Files.createTempFile(tempPath, prefix, suffix);
            res.toFile().deleteOnExit();
            return res;
        } catch (IOException e) {
            throw new FileOpException("创建临时文件失败", e);
        }
    }
    /**
     * 创建临时文件夹
     * @param prefix 前缀
     */
    public Path createTempDir(String prefix) {
        try {
            Path res = Files.createTempDirectory(tempPath, prefix);
            res.toFile().deleteOnExit();
            return res;
        } catch (IOException e) {
            throw new FileOpException("创建临时文件失败", e);
        }
    }
    /**
     * 校验绝对路径
     */
    private void validAbsolutePath(Path path, boolean isTemp) {
        path = path.normalize();
        if (isTemp) {
            if (!path.startsWith(tempPath)) {
                throw new BizException("非法路径");
            }
        } else {
            if (!path.startsWith(rootPath)) {
                throw new BizException("非法路径");
            }
        }
    }
    /**
     * 获取绝对路径
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    public Path getAbsolutePath(Path relativePath, boolean isTemp) {
        if (relativePath == null) {
            throw new BizException("路径不能为空");
        }
        // 去除头部的斜杠
        if (relativePath.startsWith("/")) {
            relativePath = Path.of(relativePath.toString().substring(1));
        }
        Path absolutePath =  isTemp ? tempPath.resolve(relativePath).normalize() : rootPath.resolve(relativePath).normalize();
        validAbsolutePath(absolutePath, isTemp);
        return absolutePath;
    }
    /**
     * 获取相对路径
     *
     * @param absolutePath 绝对路径
     * @return 相对路径
     */
    public Path getRelativePath(Path absolutePath, boolean isTemp) {
        if (absolutePath == null) {
            throw new BizException("路径不能为空");
        }
        validAbsolutePath(absolutePath, isTemp);
        return isTemp? tempPath.relativize(absolutePath).normalize() : rootPath.relativize(absolutePath).normalize();
    }
    /**
     * 获取 Resource
     */
    public Resource getResource(AttachmentDTO attachmentDTO) {
        Path path;
        if (attachmentDTO.isTemp()) {
            path = getAbsolutePath(Path.of(attachmentDTO.getRelativePath()), true);
        } else {
            if (attachmentDTO.getId() == null) {
                throw new BizException("附件 ID 不能为空");
            }
            var attach = attachmentRepository.findById(attachmentDTO.getId());
            if (attach.isEmpty()) {
                throw new BizException("附件不存在");
            }
            path = getAbsolutePath(Path.of(attach.get().getRelativePath()), false);
        }

        validAbsolutePath(path, attachmentDTO.isTemp());
        if (!Files.exists(path)) {
            throw new BizException("文件不存在");
        }
        return new FileSystemResource(path);
    }
    /**
     * 上传临时文件
     */
    public List<AttachmentDTO> uploadTemps(List<Resource> resources) {
        List<AttachmentDTO> res = new ArrayList<>();
        for (Resource resource : resources) {
            Path target = createTempFile(null, "-" + resource.getFilename());
            var file = FileUtil.upload(resource, target);
            file.path().toFile().deleteOnExit();
            AttachmentDTO attachment = new AttachmentDTO();
            attachment.setName(file.filename());
            attachment.setType(file.type());
            attachment.setTemp(true);
            attachment.setRelativePath(tempPath.relativize(target).toString());
            res.add(attachment);
        }
        return res;
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
     * @param attachmentDTOs 要更新为的附件集合
     */
    @Transactional
    public void updateRelativeAttach(Integer billId, String billNumber, BillType billType, List<AttachmentDTO> attachmentDTOs) {
        List<BillAttachRelation> oldRelations = billAttachRelationRepository.findByBillIdAndBillType(billId, billType);
        // 先将所有附件标记为待删除，根据传入的集合移除不需要删除的
        Set<Integer> removeIds = new HashSet<>();
        for (BillAttachRelation billAttachRelation : oldRelations) {
            removeIds.add(billAttachRelation.getAttach().getId());
        }
        List<FileOp> ops = new ArrayList<>();
        for (AttachmentDTO attachmentDTO : attachmentDTOs) {
            // 新增
            if (attachmentDTO.getId() == null) {
                Attachment addAttach = attachmentMapper.toEntity(attachmentDTO);
                Path originPath = getAbsolutePath(Path.of(attachmentDTO.getRelativePath()), attachmentDTO.isTemp());
                Path targetRelativePath = Path.of(billType.name()).resolve(billNumber).resolve(System.currentTimeMillis()+ "-"+ addAttach.getName());
                Path targetPath = getAbsolutePath(targetRelativePath, false);
                // 设置业务单据关联关系
                addAttach.setRelativePath(targetRelativePath.toString());
                addAttach = attachmentRepository.save(addAttach);
                BillAttachRelation billAttachRelation = new BillAttachRelation();
                billAttachRelation.setBillId(billId);
                billAttachRelation.setBillType(billType);
                billAttachRelation.setAttach(addAttach);
                billAttachRelationRepository.save(billAttachRelation);

                ops.add(new FileOp(FileOpType.MOVE, originPath, targetPath));
            } else {
                // 传入的包含该附件 ID, 则不做操作
                removeIds.remove(attachmentDTO.getId());
            }
        }
        // 删除
        for (BillAttachRelation billAttachRelation : oldRelations) {
            if (removeIds.contains(billAttachRelation.getAttach().getId())) {
                attachmentRepository.deleteById(billAttachRelation.getAttach().getId());
                billAttachRelationRepository.deleteById(billAttachRelation.getId());
                Path targetPath = getAbsolutePath(Path.of(billAttachRelation.getAttach().getRelativePath()), false);
                ops.add(new FileOp(FileOpType.DELETE, null, targetPath));
            }
        }

        FileTxUtil.exec(ops);
    }
}
