package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.model.dto.AttachmentDTO;
import com.eastwind.EACAfterSaleMgr.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

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
     * @param path 相对路径
     */
    @GetMapping("/{*path}")
    public Resource download(@PathVariable String path) {
        if (path == null) {
            throw new RuntimeException("路径不能为空");
        }
        // path拿到的路径以斜杠开始，path 会认为是绝对路径
        if (path.startsWith("/")) {
            int index = 0;
            while (index < path.length() && path.charAt(index) == '/') {
                index++;
            }
            path = path.substring(index);
        }
        return attachmentService.loadByPath(Path.of(path));
    }

    /**
     * 上传文件
     * @param file 文件
     * @param path 相对路径
     * @return 文件信息
     */
    @PostMapping
    public AttachmentDTO upload(@RequestParam MultipartFile file, @RequestParam String path) {
        return attachmentService.upload(file, Path.of(path));
    }
    /**
     * 上传文件至临时路径
     * @param file 文件
     * @return 文件信息
     */
    @PostMapping("/temp")
    public AttachmentDTO uploadTemp(@RequestParam MultipartFile file) {
        if (file == null) {
            throw new RuntimeException("文件为空");
        }
        return attachmentService.uploadTemp(file);
    }
}
