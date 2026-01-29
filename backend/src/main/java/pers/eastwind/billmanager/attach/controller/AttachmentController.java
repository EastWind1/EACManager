package pers.eastwind.billmanager.attach.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.common.exception.BizException;

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
     * @param attachment 文件 DTO
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    @GetMapping(value = "/")
    public ResponseEntity<Resource> download(AttachmentDTO attachment) {
        if (attachment == null) {
            throw new BizException("不能为空");
        }
        Resource resource = attachmentService.getResource(attachment);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().name(attachment.getName()).build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
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
