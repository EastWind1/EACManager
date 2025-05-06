package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.service.AttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/file")
public class Test2Controller {
    private final AttachmentService attachmentService;

    public Test2Controller(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    public String upload(@RequestParam MultipartFile file) {
        return attachmentService.upload(file, attachmentService.TEMP_DIR).toString();
    }

    @GetMapping("{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) throws IOException {
        Resource resource = attachmentService.loadAsResource(fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(resource.getFilename()).build().toString())
                .body(resource);
    }
}
