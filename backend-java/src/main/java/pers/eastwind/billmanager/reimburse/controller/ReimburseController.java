package pers.eastwind.billmanager.reimburse.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.reimburse.model.ReimburseDTO;
import pers.eastwind.billmanager.reimburse.model.ReimburseQueryParam;
import pers.eastwind.billmanager.reimburse.service.ReimburseIOService;
import pers.eastwind.billmanager.reimburse.service.ReimburseService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reimburse")
public class ReimburseController {
    private final ReimburseService reimburseService;
    private final ReimburseIOService reimburseIOService;

    public ReimburseController(ReimburseService reimburseService, ReimburseIOService reimburseIOService) {
        this.reimburseService = reimburseService;
        this.reimburseIOService = reimburseIOService;
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).QUERY.getCode())")
    public PageResult<ReimburseDTO> queryByParam(@RequestBody ReimburseQueryParam queryParam) {

        Page<ReimburseDTO> pageResult = reimburseService.findByParam(queryParam);
        return PageResult.fromPage(pageResult);

    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).DETAIL.getCode())")
    @GetMapping("/{id}")
    public ReimburseDTO getById(@PathVariable Integer id) {
        return reimburseService.findById(id);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).CREATE.getCode())")
    @PostMapping
    public ReimburseDTO create(@RequestBody ReimburseDTO reimburseDTO) {
        return reimburseService.create(reimburseDTO);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).UPDATE.getCode())")
    @PutMapping
    public ReimburseDTO save(@RequestBody ReimburseDTO reimburseDTO) {
        return reimburseService.update(reimburseDTO);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).DELETE.getCode())")
    @DeleteMapping
    public ActionsResult<Integer, Void> delete(@RequestBody List<Integer> ids) {
        return reimburseService.delete(ids);
    }

    @PutMapping("/process")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).PROCESS.getCode())")
    public ActionsResult<Integer, Void> process(@RequestBody List<Integer> ids) {
        return reimburseService.process(ids);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).FINISH.getCode())")
    @PutMapping("/finish")
    public ActionsResult<Integer, Void> finish(@RequestBody List<Integer> ids) {
        return reimburseService.finish(ids);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).CANCEL_PROCESS.getCode())")
    @PutMapping("/cancel-process")
    public ActionsResult<Integer, Void> cancelProcess(@RequestBody List<Integer> ids) {
        return reimburseService.cancelProcess(ids);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).CANCEL_FINISH.getCode())")
    @PutMapping("/cancel-finish")
    public ActionsResult<Integer, Void> cancelFinish(@RequestBody List<Integer> ids) {
        return reimburseService.cancelFinish(ids);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).IMPORT.getCode())")
    @PostMapping("/import")
    public ReimburseDTO importByFile(MultipartFile file) {
        return reimburseIOService.generateByFile(file.getResource());
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).EXPORT.getCode())")
    @PostMapping(value = "/export", produces = "application/octet-stream")
    public ResponseEntity<Resource> export(@RequestBody List<Integer> ids) {
        Resource resource = new FileSystemResource(reimburseIOService.export(ids));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().name("导出.zip").build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

}
