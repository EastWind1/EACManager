package pers.eastwind.billmanager.attach.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附件服务
 */
@Slf4j
@Service
public class AttachmentService implements InitializingBean {
    private static final String TEMP_PREFIX = "eac-";
    private final AttachConfigProperties properties;
    private final AttachmentRepository attachmentRepository;
    private final BillAttachRelationRepository billAttachRelationRepository;
    private final AttachmentMapper attachmentMapper;
    private final ConcurrentHashMap<Integer, String> tempFiles;
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

    public AttachmentService(AttachConfigProperties properties, AttachmentRepository attachmentRepository, BillAttachRelationRepository billAttachRelationRepository, AttachmentMapper attachmentMapper) {
        this.properties = properties;
        this.attachmentRepository = attachmentRepository;
        this.billAttachRelationRepository = billAttachRelationRepository;
        this.attachmentMapper = attachmentMapper;
        tempFiles = new ConcurrentHashMap<>();
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
     *
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
     *
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
     * 根据相对路径获取文件绝对路径
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    public Path getAbsolutePathByRela(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new BizException("路径为空");
        }
        Path path = rootPath.resolve(relativePath).normalize();
        if (!path.startsWith(rootPath)) {
            throw new BizException("非法路径");
        }
        return path;
    }


    /**
     * 根据 ID 获取文件绝对路径
     *
     * @param id 文件 ID
     * @return 绝对路径
     */
    public Path getAbsolutePathById(Integer id) {
        if (id == null) {
            throw new BizException("附件 ID 不能为空");
        }
        if (id < 0) {
            if (!tempFiles.containsKey(id)) {
                throw new BizException("文件不存在");
            }
            Path path = Path.of(tempFiles.get(id));
            if (!path.startsWith(tempPath)) {
                throw new BizException("非法路径");
            }
            return path;
        }
        var attach = attachmentRepository.findById(id);
        if (attach.isEmpty()) {
            throw new BizException("附件不存在");
        }
        Path path = rootPath.resolve(attach.get().getRelativePath()).normalize();
        if (!path.startsWith(rootPath)) {
            throw new BizException("非法路径");
        }
        return path;
    }

    /**
     * 获取 Resource
     */
    public Resource getResource(AttachmentDTO attachmentDTO) {
        Path path = getAbsolutePathById(attachmentDTO.getId());
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
            var file = FileUtil.upload(resource, tempPath);

            AttachmentDTO attachment = new AttachmentDTO();
            int id = (int) (-Math.random() * 10000);
            attachment.setId(id);
            tempFiles.put(id, file.path().toString());
            attachment.setName(file.filename());
            attachment.setType(file.type());
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
            if (attachmentDTO.getId() <= 0) {
                Attachment addAttach = attachmentMapper.toEntity(attachmentDTO);
                Path originPath = getAbsolutePathById(attachmentDTO.getId());
                Path targetRelativePath = Path.of(billType.name()).resolve(billNumber).resolve(System.currentTimeMillis() + "-" + addAttach.getName());
                Path targetPath = rootPath.resolve(targetRelativePath).normalize();
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
                Path targetPath = getAbsolutePathById(billAttachRelation.getAttach().getId());
                ops.add(new FileOp(FileOpType.DELETE, null, targetPath));
            }
        }

        FileTxUtil.exec(ops);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanTempFiles() {
        tempFiles.entrySet().removeIf(entry -> !Files.exists(Path.of(entry.getValue())));
    }
}
