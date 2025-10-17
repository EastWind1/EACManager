package pers.eastwind.billmanager.controller;

import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.mapper.AttachmentMapper;
import pers.eastwind.billmanager.service.AttachmentService;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 附件控制器
 */
@RequestMapping("/api/attachment")
@RestController
public class AttachmentController {
    private final AttachmentService attachmentService;
    private final AttachmentMapper attachmentMapper;

    public AttachmentController(AttachmentService attachmentService, AttachmentMapper attachmentMapper) {
        this.attachmentService = attachmentService;
        this.attachmentMapper = attachmentMapper;
    }

    /**
     * 下载文件
     *
     * @param path 相对路径
     */
    @GetMapping(value = "/{*path}", produces = "application/octet-stream")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    public Resource download(@PathVariable String path) {
        if (path == null) {
            throw new RuntimeException("路径不能为空");
        }
        return attachmentService.loadByPath(attachmentService.getAbsolutePath(Path.of(path)));
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param path 相对路径
     * @return 文件信息
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping
    public AttachmentDTO upload(@RequestParam MultipartFile file, @RequestParam String path) throws IOException {
        return attachmentMapper.toDTO(attachmentService.upload(file.getBytes(), file.getOriginalFilename(), attachmentService.getAbsolutePath(Path.of(path))));
    }

    /**
     * 上传文件至临时路径
     *
     * @param file 文件
     * @return 文件信息
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping("/temp")
    public AttachmentDTO uploadTemp(@RequestParam MultipartFile file) throws IOException {
        if (file == null) {
            throw new RuntimeException("文件为空");
        }
        return attachmentMapper.toDTO(attachmentService.uploadTemp(file.getBytes(), file.getOriginalFilename()));
    }
}
