package pers.eastwind.billmanager.servicebill.model;

import java.math.BigDecimal;

/**
 * 每月金额
 *
 * @param month  月份
 * @param amount 金额
 */
public record MonthSumAmount(String month, BigDecimal amount) {
}