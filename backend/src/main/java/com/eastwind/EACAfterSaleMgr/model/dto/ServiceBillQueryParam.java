package com.eastwind.EACAfterSaleMgr.model.dto;

import com.eastwind.EACAfterSaleMgr.model.common.ServiceBillState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 服务单查询参数
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceBillQueryParam extends QueryParam {
    /**
     * 单号
     */
    private String number;
    /**
     * 状态
     */
    private List<ServiceBillState> state;
    /**
     * 项目名称
     */
    private String projectName;
    /**
     * 创建起始日期
     */
    private LocalDate createdStartDate;
    /**
     * 创建结束日期
     */
    private LocalDate createdEndDate;
    /**
     * 处理完成起始日期
     */
    private LocalDate processedStartDate;
    /**
     * 处理完成结束日期
     */
    private LocalDate processedEndDate;
}
