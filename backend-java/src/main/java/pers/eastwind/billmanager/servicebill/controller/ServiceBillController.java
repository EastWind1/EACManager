package pers.eastwind.billmanager.servicebill.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.servicebill.model.AssigneeDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillQueryParam;
import pers.eastwind.billmanager.servicebill.service.ServiceBillIOService;
import pers.eastwind.billmanager.servicebill.service.ServiceBillQueryService;
import pers.eastwind.billmanager.servicebill.service.ServiceBillActionService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/serviceBill")
public class ServiceBillController {
    private final ServiceBillActionService serviceBillActionService;
    private final ServiceBillQueryService serviceBillQueryService;
    private final ServiceBillIOService serviceBillIOService;

    public ServiceBillController(ServiceBillActionService serviceBillActionService,
                                 ServiceBillQueryService serviceBillQueryService,
                                 ServiceBillIOService serviceBillIOService) {
        this.serviceBillActionService = serviceBillActionService;
        this.serviceBillQueryService = serviceBillQueryService;
        this.serviceBillIOService = serviceBillIOService;
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).QUERY.getCode())")
    public PageResult<ServiceBillDTO> queryByParam(@RequestBody ServiceBillQueryParam queryParam) {
        Page<ServiceBillDTO> pageResult = serviceBillQueryService.findByParam(queryParam);
        return PageResult.fromPage(pageResult);
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).ASSIGN.getCode())")
    public List<AssigneeDTO> getAssignees() {
        return serviceBillQueryService.findAssignees();
    }

    @PutMapping("/assign")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).ASSIGN.getCode())")
    public ActionsResult<Integer, Void> assign(@RequestBody AssignParam param) {
        return serviceBillActionService.assign(param.ids, param.assigneeIds);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).DETAIL.getCode())")
    public ServiceBillDTO getById(@PathVariable Integer id) {
        return serviceBillQueryService.findFullById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CREATE.getCode())")
    public ServiceBillDTO create(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillActionService.create(serviceBillDTO);
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).UPDATE.getCode())")
    public ServiceBillDTO update(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillActionService.update(serviceBillDTO);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).IMPORT.getCode())")
    public ServiceBillDTO importByFile(MultipartFile file) {
        return serviceBillIOService.generateByFile(file.getResource());
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).DELETE.getCode())")
    public ActionsResult<Integer, Void> delete(@RequestBody List<Integer> ids) {
        return serviceBillActionService.delete(ids);
    }

    @PutMapping("/process")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).PROCESS.getCode())")
    public ActionsResult<Integer, Void> process(@RequestBody List<Integer> ids) {
        return serviceBillActionService.process(ids);
    }

    @PutMapping("/processed")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).PROCESSED.getCode())")
    public ActionsResult<Integer, Void> processed(@RequestBody ProcessedParam param) {
        return serviceBillActionService.processed(param.ids, param.processedDate);
    }

    @PutMapping("/finish")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).FINISH.getCode())")
    public ActionsResult<Integer, Void> finish(@RequestBody FinishParam param) {
        return serviceBillActionService.finish(param.ids, param.finishedDate);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_PROCESS.getCode())")
    @PutMapping("/cancel-process")
    public ActionsResult<Integer, Void> cancelProcess(@RequestBody List<Integer> ids) {
        return serviceBillActionService.cancelProcess(ids);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_PROCESSED.getCode())")
    @PutMapping("/cancel-processed")
    public ActionsResult<Integer, Void> cancelProcessed(@RequestBody List<Integer> ids) {
        return serviceBillActionService.cancelProcessed(ids);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_FINISH.getCode())")
    @PutMapping("/cancel-finish")
    public ActionsResult<Integer, Void> cancelFinish(@RequestBody List<Integer> ids) {
        return serviceBillActionService.cancelFinish(ids);
    }

    @PostMapping(value = "/export", produces = "application/octet-stream")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).EXPORT.getCode())")
    public ResponseEntity<Resource> export(@RequestBody List<Integer> ids) {
        Resource resource = new FileSystemResource(serviceBillIOService.export(ids));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().name("导出.zip").build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    public record AssignParam(List<Integer> ids, List<Integer> assigneeIds) {
    }

    public record ProcessedParam(List<Integer> ids, Instant processedDate) {
    }

    public record FinishParam(List<Integer> ids, Instant finishedDate) {
    }
}
