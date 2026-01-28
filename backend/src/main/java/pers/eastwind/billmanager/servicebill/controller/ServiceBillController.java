package pers.eastwind.billmanager.servicebill.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillQueryParam;
import pers.eastwind.billmanager.servicebill.service.ServiceBillBizService;
import pers.eastwind.billmanager.servicebill.service.ServiceBillIOService;

import java.time.Instant;
import java.util.List;

/**
 * 服务单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/serviceBill")
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class ServiceBillController {
    private final ServiceBillBizService serviceBillBizService;
    private final ServiceBillIOService serviceBillIOService;

    public ServiceBillController(ServiceBillBizService serviceBillBizService, ServiceBillIOService serviceBillIOService) {
        this.serviceBillBizService = serviceBillBizService;
        this.serviceBillIOService = serviceBillIOService;
    }

    /**
     * 根据条件查找服务单
     *
     * @return 服务单列表
     */
    @PostMapping("/query")
    public PageResult<ServiceBillDTO> queryByParam(@RequestBody ServiceBillQueryParam queryParam) {

        Page<ServiceBillDTO> pageResult = serviceBillBizService.findByParam(queryParam);
        return PageResult.fromPage(pageResult);

    }

    /**
     * 根据 ID 获取服务单
     */
    @GetMapping("/{id}")
    public ServiceBillDTO getById(@PathVariable Integer id) {
        return serviceBillBizService.findById(id);
    }

    /**
     * 创建服务单
     *
     * @param serviceBillDTO 服务单
     * @return 保存后的服务单
     */
    @PostMapping
    public ServiceBillDTO create(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillBizService.create(serviceBillDTO);
    }

    /**
     * 保存服务单
     *
     * @param serviceBillDTO 服务单
     * @return 保存后的服务单
     */
    @PutMapping
    public ServiceBillDTO save(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillBizService.update(serviceBillDTO);
    }

    /**
     * 通过文件创建
     */
    @PostMapping("/import")
    public ServiceBillDTO importByFile(MultipartFile file)  {
        return serviceBillIOService.generateByFile(file.getResource());
    }

    /**
     * 删除服务单
     */
    @DeleteMapping
    public ActionsResult<Integer, Void> delete(@RequestBody List<Integer> ids) {
        return serviceBillBizService.delete(ids);
    }

    /**
     * 处理服务单
     */
    @PutMapping("/process")
    public ActionsResult<Integer, Void> process(@RequestBody List<Integer> ids) {
        return serviceBillBizService.process(ids);
    }

    /**
     * 处理完成服务单
     */
    @PutMapping("/processed")
    public ActionsResult<Integer, Void> processed(@RequestBody ProcessedParam param) {
        return serviceBillBizService.processed(param.ids, param.processedDate);
    }

    /**
     * 完成服务单
     */
    @PutMapping("/finish")
    public ActionsResult<Integer, Void> finish(@RequestBody FinishParam param) {
        return serviceBillBizService.finish(param.ids, param.finishedDate);
    }

    /**
     * 导出
     */
    @PostMapping(value = "/export", produces = "application/octet-stream")
    public Resource export(@RequestBody List<Integer> ids) {
        return FileUtil.loadByPath(serviceBillIOService.export(ids));
    }

    /**
     * 处理完成参数
     */
    public record ProcessedParam(List<Integer> ids, Instant processedDate) {
    }
    /**
     * 完成参数
     */
    public record FinishParam(List<Integer> ids, Instant finishedDate) {}
}
