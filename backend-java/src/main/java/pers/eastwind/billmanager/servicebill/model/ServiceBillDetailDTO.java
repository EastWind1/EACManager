package pers.eastwind.billmanager.servicebill.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceBillDetailDTO {
    private Integer id;
    @NotBlank(message = "设备类型不能为空")
    @Size(max = 100, message = "设备类型不能超过 100 个字符")
    private String device;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String remark;
}
