package pers.eastwind.billmanager.attach.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import pers.eastwind.billmanager.attach.repository.AttachRepository;
import pers.eastwind.billmanager.attach.util.FileTxUtil;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.exception.FileOpException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 附件以「namespace/唯一id」为 key 存储，写入后不再移动，故不存在难以原子的“改库 + 搬文件”操作。
 * 上传即落库为暂存，单据提交时置为已提交，暂存垃圾按状态回收；关联表由各业务模块自行维护。
 */
@Slf4j
@Service
public class AttachService implements InitializingBean {
    private static final String TEMP_PREFIX = "eac-";
    private static final Duration TEMP_RETENTION = Duration.ofDays(7);
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final AttachConfigProperties properties;
    private final AttachRepository attachRepository;
    private final AttachMapper attachMapper;
    @PersistenceContext
    private EntityManager entityManager;
    @Getter
    private Path rootPath;
    @Getter
    private Path tempPath;

    public AttachService(AttachConfigProperties properties, AttachRepository attachRepository, AttachMapper attachMapper) {
        this.properties = properties;
        this.attachRepository = attachRepository;
        this.attachMapper = attachMapper;
    }

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

    public Path createTempFile(String prefix, String suffix) {
        try {
            Path res = Files.createTempFile(tempPath, prefix, suffix);
            res.toFile().deleteOnExit();
            return res;
        } catch (IOException e) {
            throw new FileOpException("创建临时文件失败", e);
        }
    }

    public Path createTempDir(String prefix) {
        try {
            Path res = Files.createTempDirectory(tempPath, prefix);
            res.toFile().deleteOnExit();
            return res;
        } catch (IOException e) {
            throw new FileOpException("创建临时文件失败", e);
        }
    }

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

    public Path getAbsolutePathById(Integer id) {
        if (id == null) {
            throw new BizException("附件 ID 不能为空");
        }
        Attach attachment = attachRepository.findById(id).orElse(null);
        if (attachment == null) {
            throw new BizException("附件不存在");
        }
        return getAbsolutePathByRela(attachment.getRelativePath());
    }

    public Resource getResource(AttachDTO attachDTO) {
        Path path = getAbsolutePathById(attachDTO.getId());
        if (!Files.exists(path)) {
            throw new BizException("文件不存在");
        }
        return new FileSystemResource(path);
    }

    @Transactional
    public List<AttachDTO> upload(String namespace, List<Resource> resources) {
        if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new BizException("附件命名空间不合法");
        }
        if (resources == null || resources.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        List<AttachDTO> res = new ArrayList<>();
        for (Resource resource : resources) {
            res.add(attachMapper.toDTO(saveOne(namespace, resource)));
        }
        return res;
    }

    private Attach saveOne(String namespace, Resource resource) {
        String fileName = resource.getFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new BizException("文件名不能为空");
        }

        String relativePath = namespace + "/" + UUID.randomUUID() + "-" + fileName;
        Path targetPath = getAbsolutePathByRela(relativePath);

        Attach attachment = new Attach();
        attachment.setName(fileName);
        attachment.setRelativePath(relativePath);
        attachment.setStatus(AttachStatus.TRANSIENT);
        try {
            Files.createDirectories(targetPath.getParent());
            attachment.setType(FileUtil.save(resource, targetPath));
            return attachRepository.save(attachment);
        } catch (IOException | RuntimeException e) {
            FileUtil.deleteQuietly(targetPath);
            throw new FileOpException("保存文件失败", e);
        }
    }


    @Transactional
    public void persist(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return;
        }
        int affected = attachRepository.persistByIds(ids);
        if (affected != ids.size()) {
            throw new BizException("附件不存在或已被提交");
        }
        // 更新使用的 JPQL，JPA 不会刷新一级缓存，若之后有同一事务查询，会取到旧数据
        for (Integer id : new HashSet<>(ids)) {
            Attach attachment = entityManager.find(Attach.class, id);
            if (attachment != null && attachment.getStatus() != AttachStatus.PERSISTENT) {
                entityManager.refresh(attachment);
            }
        }
    }

    @Transactional
    public void delete(Collection<Integer> ids) {
        Set<Integer> uniqueIds = new HashSet<>(ids);
        if (ids.isEmpty()) {
            return;
        }

        List<Attach> attachments = attachRepository.findAllById(uniqueIds);
        if (attachments.size() != uniqueIds.size()) {
            throw new BizException("附件不存在");
        }
        attachRepository.deleteAll(attachments);

        List<FileOp> ops = new ArrayList<>();
        for (Attach attachment : attachments) {
            Path targetPath = getAbsolutePathByRela(attachment.getRelativePath());
            if (!Files.exists(targetPath)) {
                continue;
            }
            ops.add(new FileOp(FileOpType.DELETE, null, targetPath));
        }
        FileTxUtil.exec(ops);
    }

    /**
     * 清理超过 7 天的游离附件及其磁盘文件，每日 02:00 执行。
     */
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanTransientFiles() {
        List<Attach> expired = attachRepository.findByStatusAndCreatedDateBefore(AttachStatus.TRANSIENT, Instant.now().minus(TEMP_RETENTION));
        if (expired.isEmpty()) {
            return;
        }
        // 一条 delete ... where id in (...) 回收，避免逐行删除；随后清理一级缓存里的旧状态
        attachRepository.deleteAllInBatch(expired);
        entityManager.clear();

        List<FileOp> ops = new ArrayList<>();
        for (Attach attachment : expired) {
            Path targetPath = getAbsolutePathByRela(attachment.getRelativePath());
            if (!Files.exists(targetPath)) {
                continue;
            }
            ops.add(new FileOp(FileOpType.DELETE, null, targetPath));
        }
        try {
            FileTxUtil.exec(ops);
        } catch (Exception e) {
            log.error("回收游离附件文件失败", e);
        }
        log.info("回收游离附件 {} 个", expired.size());
    }


}
