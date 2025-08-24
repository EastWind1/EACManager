package pers.eastwind.billmanager.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.model.dto.ActionsResult;
import pers.eastwind.billmanager.model.dto.PageResult;
import pers.eastwind.billmanager.model.dto.ReimburseQueryParam;
import pers.eastwind.billmanager.model.dto.ReimbursementDTO;
import pers.eastwind.billmanager.service.AttachmentService;
import pers.eastwind.billmanager.service.ReimburseService;

import java.util.List;

/**
 * 报销单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/reimburse")
public class ReimburseController {
    private final ReimburseService reimburseService;
    private final AttachmentService attachmentService;

    public ReimburseController(ReimburseService reimburseService, AttachmentService attachmentService) {
        this.reimburseService = reimburseService;
        this.attachmentService = attachmentService;
    }

    /**
     * 根据条件查找报销单
     *
     * @return 报销单列表
     */
    @PostMapping("/query")
    public PageResult<ReimbursementDTO> queryByParam(@RequestBody ReimburseQueryParam queryParam) {

        Page<ReimbursementDTO> pageResult = reimburseService.findByParam(queryParam);
        return PageResult.fromPage(pageResult);

    }

    /**
     * 根据 ID 获取报销单
     */
    @GetMapping("/{id}")
    public ReimbursementDTO getById(@PathVariable Integer id) {
        return reimburseService.findById(id);
    }

    /**
     * 创建报销单
     *
     * @param reimbursementDTO 报销单
     * @return 保存后的报销单
     */
    @PostMapping
    public ReimbursementDTO create(@RequestBody ReimbursementDTO reimbursementDTO) {
        return reimburseService.create(reimbursementDTO);
    }

    /**
     * 保存报销单
     *
     * @param reimbursementDTO 报销单
     * @return 保存后的报销单
     */
    @PutMapping
    public ReimbursementDTO save(@RequestBody ReimbursementDTO reimbursementDTO) {
        return reimburseService.update(reimbursementDTO);
    }

    /**
     * 删除报销单
     */
    @DeleteMapping
    public ActionsResult<Integer, Void> delete(@RequestBody List<Integer> ids) {
        return reimburseService.delete(ids);
    }

    /**
     * 处理报销单
     */
    @PutMapping("/process")
    public ActionsResult<Integer, Void> process(@RequestBody List<Integer> ids) {
        return reimburseService.process(ids);
    }

    /**
     * 处理完成报销单
     */
    @PutMapping("/finish")
    public ActionsResult<Integer, Void> finish(@RequestBody List<Integer> ids) {
        return reimburseService.finish(ids);
    }

    /**
     * 导出
     */
    @PostMapping("/export")
    public Resource export(@RequestBody List<Integer> ids) {
        return attachmentService.loadByPath(reimburseService.export(ids));
    }

}
