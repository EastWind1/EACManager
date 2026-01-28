package pers.eastwind.billmanager.attach.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件控制器
 */
@RequestMapping("/api/attachment")
@RestController
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 下载文件
     *
     * @param path 相对路径
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    @GetMapping(value = "/{*path}", produces = "application/octet-stream")
    public Resource download(@PathVariable String path) {
        if (path == null) {
            throw new BizException("路径不能为空");
        }
        return FileUtil.loadByPath(attachmentService.getAbsolutePath(Path.of(path)));
    }

    /**
     * 根据 ID 下载文件
     *
     * @param id 文件 ID
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    @GetMapping(value = "/v2/{id}", produces = "application/octet-stream")
    public ResponseEntity<Resource> downloadById(@PathVariable Integer id) {
        if (id == null) {
            throw new BizException("ID 不能为空");
        }
        AttachmentDTO attach = attachmentService.getById(id);
        Resource resource = FileUtil.loadByPath(attachmentService.getAbsolutePath(Path.of(attach.getRelativePath())));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + attach.getName() + "\"")
                .body(resource);
    }


    /**
     * 上传文件至临时路径
     *
     * @param files 文件
     * @return 文件信息
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping("/temp")
    public List<AttachmentDTO> uploadTemp(@RequestParam List<MultipartFile> files) {
        List<Resource> resources = new ArrayList<>();
        for (MultipartFile file : files) {
            resources.add(file.getResource());
        }
        return attachmentService.uploadTemps(resources);
    }
}
