package pers.eastwind.billmanager.model.dto;

import pers.eastwind.billmanager.model.common.ServiceBillState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
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
     * 下单起始日期
     */
    private Instant orderStartDate;
    /**
     * 下单结束日期
     */
    private Instant orderEndDate;
    /**
     * 处理完成起始日期
     */
    private Instant processedStartDate;
    /**
     * 处理完成结束日期
     */
    private Instant processedEndDate;
}
