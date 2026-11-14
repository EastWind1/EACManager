package pers.eastwind.billmanager.reimburse.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReimburseDetailDTO {
    private Integer id;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 255, message = "项目名称不能超过 255 个字符")
    private String name;

    private BigDecimal amount;
}
