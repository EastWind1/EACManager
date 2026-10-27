package pers.eastwind.billmanager.common.model;

import java.math.BigDecimal;

/**
 * 按年月分组的金额行；统计查询的构造器投影目标（JPQL {@code select new}），避免用 {@code Object[]} 取值
 */
public record MonthAmountRow(Integer year, Integer month, BigDecimal amount) {
}
