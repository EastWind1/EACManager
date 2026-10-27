package pers.eastwind.billmanager.reimburse.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReimburseDetailDTO {
    private Integer id;

    private String name;

    private BigDecimal amount;
}
