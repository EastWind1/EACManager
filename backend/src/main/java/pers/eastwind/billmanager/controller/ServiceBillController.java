package pers.eastwind.billmanager.controller;

import pers.eastwind.billmanager.model.dto.ActionsResult;
import pers.eastwind.billmanager.model.dto.PageResult;
import pers.eastwind.billmanager.model.dto.ServiceBillDTO;
import pers.eastwind.billmanager.model.dto.ServiceBillQueryParam;
import pers.eastwind.billmanager.service.ServiceBillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 服务单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/serviceBill")
public class ServiceBillController {
    private final ServiceBillService serviceBillService;

    public ServiceBillController(ServiceBillService serviceBillService) {
        this.serviceBillService = serviceBillService;
    }


    /**
     * 根据条件查找服务单
     *
     * @return 服务单列表
     */
    @PostMapping("/query")
    public PageResult<ServiceBillDTO> queryByParam(@RequestBody ServiceBillQueryParam queryParam) {

        Page<ServiceBillDTO> pageResult = serviceBillService.findByParam(queryParam);
        return PageResult.fromPage(pageResult);

    }

    /**
     * 根据 ID 获取服务单
     */
    @GetMapping("/{id}")
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
    public ServiceBillDTO save(@RequestBody ServiceBillDTO serviceBillDTO) {
        return serviceBillService.update(serviceBillDTO);
    }
    /**
     * 通过文件创建
     */
    @PostMapping("/import")
    public ServiceBillDTO importByFile(MultipartFile file) {
        return serviceBillService.generateByFile(file);
    }
    /**
     * 删除服务单
     */
    @DeleteMapping
    public ActionsResult<Integer, Void> delete(@RequestBody List<Integer> ids) {
        return serviceBillService.delete(ids);
    }
    /**
     * 处理服务单
     */
    @PutMapping("/process")
    public ActionsResult<Integer, Void> process(@RequestBody List<Integer> ids) {
        return serviceBillService.process(ids);
    }

    /**
     * 处理完成参数
     */
    public record ProcessedParam(List<Integer> ids, LocalDate processedDate) {}
    /**
     * 处理完成服务单
     */
    @PutMapping("/processed")
    public ActionsResult<Integer, Void> processed(@RequestBody ProcessedParam param) {
        return serviceBillService.processed(param.ids, param.processedDate);
    }
    /**
     * 完成服务单
     */
    @PutMapping("/finish")
    public ActionsResult<Integer, Void> finish(@RequestBody List<Integer> ids) {
        return serviceBillService.finish(ids);
    }
}
