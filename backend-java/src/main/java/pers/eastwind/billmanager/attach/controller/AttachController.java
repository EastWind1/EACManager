package pers.eastwind.billmanager.attach.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.common.exception.BizException;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/api/attachment")
@RestController
public class AttachController {
    private final AttachService attachService;

    public AttachController(AttachService attachService) {
        this.attachService = attachService;
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.attach.permission.AttachPermission).DOWNLOAD.getCode())")
    @GetMapping(value = "/")
    public ResponseEntity<Resource> download(AttachDTO attachment) {
        if (attachment == null) {
            throw new BizException("不能为空");
        }
        Resource resource = attachService.getResource(attachment);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().name(attachment.getName()).build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.attach.permission.AttachPermission).UPLOAD.getCode())")
    @PostMapping("/upload")
    public List<AttachDTO> upload(@RequestParam String namespace, @RequestParam List<MultipartFile> files) {
        List<Resource> resources = new ArrayList<>();
        for (MultipartFile file : files) {
            resources.add(file.getResource());
        }
        return attachService.upload(namespace, resources);
    }
}
