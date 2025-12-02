package pers.eastwind.billmanager.controller;

import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.mapper.AttachmentMapper;
import pers.eastwind.billmanager.service.AttachmentService;

import java.io.IOException;
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
     * @param files 文件
     * @param path 相对路径
     * @return 文件信息
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping
    public List<AttachmentDTO> upload(@RequestParam List<MultipartFile> files, @RequestParam String path) throws IOException {
        throw new RuntimeException("暂不支持上传文件到指定路径");
//        List<Resource> resources = new ArrayList<>();
//        for (MultipartFile file : files) {
//            resources.add(file.getResource());
//        }
//        List<AttachmentDTO> attachments = new ArrayList<>();
//        for (Attachment attachment : attachmentService.upload(resources, attachmentService.getAbsolutePath(Path.of(path)))) {
//            attachments.add(attachmentMapper.toDTO(attachment));
//        }
//        return attachments;
    }

    /**
     * 上传文件至临时路径
     *
     * @param files 文件
     * @return 文件信息
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping("/temp")
    public List<AttachmentDTO> uploadTemp(@RequestParam List<MultipartFile> files) throws IOException {
        List<Resource> resources = new ArrayList<>();
        for (MultipartFile file : files) {
            resources.add(file.getResource());
        }
        List<AttachmentDTO> attachments = new ArrayList<>();
        for (Attachment attachment : attachmentService.uploadTemp(resources)) {
            attachments.add(attachmentMapper.toDTO(attachment));
        }
        return attachments;
    }
}
