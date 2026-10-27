package pers.eastwind.billmanager.servicebill.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceBillDetailDTO {
    private Integer id;
    private String device;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String remark;
}
