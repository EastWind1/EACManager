package pers.eastwind.billmanager.common.model;

import java.math.BigDecimal;

/**
 * 每月金额，用于仪表盘统计显示
 */
public record MonthSumAmount(String month, BigDecimal amount) {
}
