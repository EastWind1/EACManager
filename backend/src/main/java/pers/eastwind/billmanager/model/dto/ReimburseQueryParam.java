package pers.eastwind.billmanager.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;

/**
 * 服务单查询参数
 */

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReimburseQueryParam extends QueryParam {
    /**
     * 单号
     */
    private String number;
    /**
     * 备注
     */
    private String remark;
    /**
     * 报销起始日期
     */
    private Instant reimburseStartDate;
    /**
     * 报销结束日期
     */
    private Instant reimburseEndDate;
}
