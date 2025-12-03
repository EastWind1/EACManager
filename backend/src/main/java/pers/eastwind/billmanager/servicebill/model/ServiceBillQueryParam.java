package pers.eastwind.billmanager.servicebill.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import pers.eastwind.billmanager.common.model.QueryParam;

import java.time.Instant;
import java.util.List;

/**
 * 服务单查询参数
 */

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ServiceBillQueryParam extends QueryParam {
    /**
     * 单号
     */
    private String number;
    /**
     * 状态
     */
    private List<ServiceBillState> states;
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
