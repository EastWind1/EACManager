package pers.eastwind.billmanager.servicebill.controller;

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
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillQueryParam;
import pers.eastwind.billmanager.servicebill.service.ServiceBillIOService;
import pers.eastwind.billmanager.servicebill.service.ServiceBillService;

import java.time.Instant;
import java.util.List;

/**
 * 服务单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/serviceBill")
public class ServiceBillController {
    private final ServiceBillService serviceBillService;
    private final ServiceBillIOService serviceBillIOService;

    public ServiceBillController(ServiceBillService serviceBillService, ServiceBillIOService serviceBillIOService) {
        this.serviceBillService = serviceBillService;
        this.serviceBillIOService = serviceBillIOService;
    }

    /**
     * 根据条件查找服务单
     *
     * @return 服务单列表
     */
    @PostMapping("/query")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).QUERY.getCode())")
    public PageResult<ServiceBillDTO> queryByParam(@RequestBody ServiceBillQueryParam queryParam) {
        Page<ServiceBillDTO> pageResult = serviceBillService.findByParam(queryParam);
        return PageResult.fromPage(pageResult);
    }

    /**
     * 根据 ID 获取服务单
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).DETAIL.getCode())")
    public ServiceBillDTO getById(@PathVariable Integer id) {
        return serviceBillService.findById(id);
    }

    /**
     * 创建服务单
     *
     * @param serviceBillDTO 服务单
     * @return 保存后的服务单
     */
    @PostMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CREATE.getCode())")
    public ServiceBillDTO create(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillService.create(serviceBillDTO);
    }

    /**
     * 保存服务单
     *
     * @param serviceBillDTO 服务单
     * @return 保存后的服务单
     */
    @PutMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).UPDATE.getCode())")
    public ServiceBillDTO update(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillService.update(serviceBillDTO);
    }

    /**
     * 通过文件创建
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).IMPORT.getCode())")
    public ServiceBillDTO importByFile(MultipartFile file) {
        return serviceBillIOService.generateByFile(file.getResource());
    }

    /**
     * 删除服务单
     */
    @DeleteMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).DELETE.getCode())")
    public ActionsResult<Integer, Void> delete(@RequestBody List<Integer> ids) {
        return serviceBillService.delete(ids);
    }

    /**
     * 处理服务单
     */
    @PutMapping("/process")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).PROCESS.getCode())")
    public ActionsResult<Integer, Void> process(@RequestBody List<Integer> ids) {
        return serviceBillService.process(ids);
    }

    /**
     * 处理完成服务单
     */
    @PutMapping("/processed")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).PROCESSED.getCode())")
    public ActionsResult<Integer, Void> processed(@RequestBody ProcessedParam param) {
        return serviceBillService.processed(param.ids, param.processedDate);
    }

    /**
     * 完成服务单
     */
    @PutMapping("/finish")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).FINISH.getCode())")
    public ActionsResult<Integer, Void> finish(@RequestBody FinishParam param) {
        return serviceBillService.finish(param.ids, param.finishedDate);
    }

    /**
     * 取消处理服务单 - 管理员权限
     */
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_PROCESS.getCode())")
    @PutMapping("/cancel-process")
    public ActionsResult<Integer, Void> cancelProcess(@RequestBody List<Integer> ids) {
        return serviceBillService.cancelProcess(ids);
    }

    /**
     * 取消处理完成 - 管理员权限
     */
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_PROCESSED.getCode())")
    @PutMapping("/cancel-processed")
    public ActionsResult<Integer, Void> cancelProcessed(@RequestBody List<Integer> ids) {
        return serviceBillService.cancelProcessed(ids);
    }

    /**
     * 取消完成 - 管理员权限
     */
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_FINISH.getCode())")
    @PutMapping("/cancel-finish")
    public ActionsResult<Integer, Void> cancelFinish(@RequestBody List<Integer> ids) {
        return serviceBillService.cancelFinish(ids);
    }

    /**
     * 导出
     */
    @PostMapping(value = "/export", produces = "application/octet-stream")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).EXPORT.getCode())")
    public ResponseEntity<Resource> export(@RequestBody List<Integer> ids) {
        Resource resource = new FileSystemResource(serviceBillIOService.export(ids));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().name("导出.zip").build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    /**
     * 处理完成参数
     */
    public record ProcessedParam(List<Integer> ids, Instant processedDate) {
    }

    /**
     * 完成参数
     */
    public record FinishParam(List<Integer> ids, Instant finishedDate) {
    }
}
