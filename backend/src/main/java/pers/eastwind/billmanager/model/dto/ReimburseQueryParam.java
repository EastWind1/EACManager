package pers.eastwind.billmanager.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import pers.eastwind.billmanager.model.common.ReimburseState;

import java.time.Instant;
import java.util.List;

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
     * 摘要
     */
    private String summary;
    /**
     * 状态
     */
    private List<ReimburseState> states;
    /**
     * 报销起始日期
     */
    private Instant reimburseStartDate;
    /**
     * 报销结束日期
     */
    private Instant reimburseEndDate;
}
