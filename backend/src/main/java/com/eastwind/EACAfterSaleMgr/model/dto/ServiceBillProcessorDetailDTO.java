package com.eastwind.EACAfterSaleMgr.model.dto;


import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务单处理人明细 DTO
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
    private LocalDateTime acceptDate;

    /**
     * 处理完成时间
     */
    private LocalDateTime processedDate;
}
