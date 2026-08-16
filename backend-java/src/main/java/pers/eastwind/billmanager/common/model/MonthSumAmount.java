package pers.eastwind.billmanager.common.model;

import java.math.BigDecimal;

/**
 * 每月金额
 * 用于仪表盘统计显示
 *
 * @param month  月份
 * @param amount 金额
 */
public record MonthSumAmount(String month, BigDecimal amount) {
}
