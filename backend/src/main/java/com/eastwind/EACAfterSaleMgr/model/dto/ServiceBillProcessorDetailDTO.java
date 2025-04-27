package com.eastwind.EACAfterSaleMgr.model.dto;


import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 服务单处理人明细DTO
 */
@Data
public class ServiceBillProcessorDetailDTO {

    private Integer id;
    /**
     * 处理人
     */
    private UserDTO processUser;

    /*
     * 处理数量
     */
    private BigDecimal processCount;

    /**
     * 处理金额
     */
    private BigDecimal processedAmount;
    /**
     * 接受时间
     */
    private ZonedDateTime acceptDate;

    /**
     * 处理完成时间
     */
    private ZonedDateTime processedDate;

    // TODO: 报销单列表

}
