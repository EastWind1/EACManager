package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.model.dto.AttachmentDTO;
import com.eastwind.EACAfterSaleMgr.model.mapper.AttachmentMapper;
import com.eastwind.EACAfterSaleMgr.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@RequestMapping("/api/attachment")
@RestController
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{*path}")
    public Resource download(@PathVariable String path) {
        if (path == null) {
            throw new RuntimeException("路径为空");
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

    @PostMapping
    public AttachmentDTO upload(@RequestParam MultipartFile file, @RequestParam String path) {
        if (file == null) {
            throw new RuntimeException("文件为空");
        }
        if (path == null) {
            throw new RuntimeException("路径为空");
        }
        return attachmentService.upload(file, Path.of(path));
    }
    @PostMapping("/temp")
    public AttachmentDTO upload(@RequestParam MultipartFile file) {
        if (file == null) {
            throw new RuntimeException("文件为空");
        }
        return attachmentService.uploadTemp(file);
    }
}
