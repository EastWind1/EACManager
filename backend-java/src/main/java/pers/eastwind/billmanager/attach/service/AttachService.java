package pers.eastwind.billmanager.attach.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.attach.config.AttachPaths;
import pers.eastwind.billmanager.attach.model.*;
import pers.eastwind.billmanager.attach.repository.AttachRepository;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.attach.util.SafePath;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.exception.FileOpException;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.util.AuthUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 附件以「namespace/唯一id」为 key 存储，写入后不再移动，故不存在难以原子的“改库 + 搬文件”操作。
 * 上传即落库为暂存，单据提交时置为已提交，暂存垃圾按状态回收；关联表由各业务模块自行维护。
 */
@Slf4j
@Service
public class AttachService {
    private static final Duration TEMP_RETENTION = Duration.ofDays(7);
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final AttachRepository attachRepository;
    private final AttachMapper attachMapper;
    private final FileTxService fileTxService;
    private final Path rootPath;
    private final Path tempPath;
    @PersistenceContext
    private EntityManager entityManager;

    public AttachService(
            AttachPaths paths,
            AttachRepository attachRepository,
            AttachMapper attachMapper,
            FileTxService fileTxService
    ) {
        this.attachRepository = attachRepository;
        this.attachMapper = attachMapper;
        this.fileTxService = fileTxService;
        this.rootPath = paths.root();
        this.tempPath = paths.temp();
    }

    public Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(tempPath, prefix, suffix);
        } catch (IOException e) {
            throw new FileOpException("创建临时文件失败", e);
        }
    }

    public Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(tempPath, prefix);
        } catch (IOException e) {
            throw new FileOpException("创建临时文件失败", e);
        }
    }

    /**
     * 把附件复制到目标目录。
     */
    public void copyTo(Collection<Integer> ids, Path targetDir) {
        Map<Integer, AttachFile> files = getPaths(ids);
        if (files.isEmpty()) {
            return;
        }
        List<FileOp> ops = new ArrayList<>(files.size());
        Set<String> used = new HashSet<>();
        for (AttachFile file : files.values()) {
            String name = uniqueName(targetDir, used, file.attachment().getName());
            ops.add(new FileOp(FileOpType.COPY, file.path(), targetDir.resolve(name)));
        }
        fileTxService.exec(ops);
    }

    private static String uniqueName(Path targetDir, Set<String> used, String name) {
        String base = name == null || name.isBlank() ? "attachment" : name;
        String candidate = base;
        int repeat = 1;
        while (!used.add(candidate) || Files.exists(targetDir.resolve(candidate))) {
            candidate = repeat++ + "-" + base;
        }
        return candidate;
    }

    /**
     * 打包临时目录，压缩包放在附件临时目录下。
     */
    public Path zipDirectory(Path sourceDir) {
        Path zip = tempPath.resolve(sourceDir.getFileName() + ".zip");
        return FileUtil.zip(sourceDir, zip);
    }

    private Path getAbsolutePathByRela(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new BizException("路径为空");
        }
        return SafePath.of(rootPath).resolve(relativePath).toPath();
    }

    /**
     * 批量读取附件，返回 DTO 与磁盘路径。
     */
    public Map<Integer, AttachFile> getPaths(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.contains(null)) {
            throw new BizException("附件 ID 不能为空");
        }
        List<Attach> attachments = attachRepository.findAllById(uniqueIds);
        if (attachments.size() != uniqueIds.size()) {
            throw new BizException("附件不存在");
        }
        Map<Integer, AttachFile> result = new HashMap<>(attachments.size());
        for (Attach attachment : attachments) {
            assertCanAccess(attachment);
            Path path = getAbsolutePathByRela(attachment.getRelativePath());
            if (!Files.exists(path)) {
                throw new BizException("文件不存在");
            }
            result.put(attachment.getId(), new AttachFile(attachMapper.toDTO(attachment), path));
        }
        return result;
    }

    /**
     * 读取单个附件。
     */
    public AttachFile getPath(Integer id) {
        if (id == null) {
            throw new BizException("附件 ID 不能为空");
        }
        AttachFile file = getPaths(List.of(id)).get(id);
        if (file == null) {
            throw new BizException("附件不存在");
        }
        return file;
    }

    /**
     * 未绑定单据的瞬时附件只允许上传者本人（或管理员）读取：
     * 否则任何拥有附件下载权限的人都能按自增 ID 遍历他人刚上传的文件。
     */
    private void assertCanAccess(Attach attachment) {
        if (attachment.getStatus() != AttachStatus.TRANSIENT) {
            return;
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new BizException("未登录");
        }
        if (attachment.getCreatedById() != null
                && !attachment.getCreatedById().equals(curUser.getId())
                && !AuthUtil.hasRole("ROLE_ADMIN")) {
            throw new BizException("无权访问该附件");
        }
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
        FileUtil.validName(fileName);

        // 存储 key 只用服务端生成的 hash；原始文件名只作为 attachment.name 留在内存/数据库
        String relativePath = namespace + "/" + UUID.randomUUID();
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
        Set<Integer> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.isEmpty()) {
            return;
        }
        List<Attach> attachments = attachRepository.findAllById(uniqueIds);
        if (attachments.size() != uniqueIds.size()) {
            throw new BizException("附件不存在");
        }
        // 只能提交自己刚上传、且仍处于瞬时态的附件，避免把别人的临时附件绑到自己的单据
        for (Attach attachment : attachments) {
            if (attachment.getStatus() != AttachStatus.TRANSIENT) {
                throw new BizException("附件不存在或已被提交");
            }
            assertCanAccess(attachment);
        }
        int affected = attachRepository.persistByIds(uniqueIds);
        if (affected != uniqueIds.size()) {
            throw new BizException("附件不存在或已被提交");
        }
        // 更新使用的 JPQL，JPA 不会刷新一级缓存，若之后有同一事务查询，会取到旧数据
        for (Integer id : uniqueIds) {
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
        fileTxService.exec(ops);
    }

    /**
     * 清理超过 7 天的游离附件及其磁盘文件，每日 02:00 执行。
     */
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanTransientFiles() {
        cleanTransientFiles(Instant.now().minus(TEMP_RETENTION));
        cleanTempFiles(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    /**
     * 按截止时间清理游离附件；测试用未来时间戳触发回收，避免改库伪造创建时间。
     */
    @Transactional
    public void cleanTransientFiles(Instant cutoff) {
        List<Attach> expired = attachRepository.findByStatusAndCreatedDateBefore(AttachStatus.TRANSIENT, cutoff);
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
            fileTxService.exec(ops);
        } catch (Exception e) {
            log.error("回收游离附件文件失败", e);
        }
        log.info("回收游离附件 {} 个", expired.size());
    }

    /**
     * 回收临时目录中超过一天的文件与空目录。
     */
    private void cleanTempFiles(Instant cutoff) {
        if (tempPath == null || !Files.isDirectory(tempPath)) {
            return;
        }
        try (Stream<Path> files = Files.walk(tempPath)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(FileUtil::deleteQuietly);
        } catch (IOException e) {
            log.error("清理临时文件失败", e);
            return;
        }
        try (Stream<Path> dirs = Files.walk(tempPath)) {
            dirs.sorted(Comparator.reverseOrder())
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(tempPath))
                    .forEach(this::deleteIfEmpty);
        } catch (IOException e) {
            log.error("清理临时目录失败", e);
        }
    }

    private void deleteIfEmpty(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            log.debug("删除临时目录失败: {}", dir, e);
        }
    }


}
