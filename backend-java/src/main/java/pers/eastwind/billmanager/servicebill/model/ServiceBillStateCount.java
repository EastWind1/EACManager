package pers.eastwind.billmanager.servicebill.model;

/**
 * 服务单状态计数行，作为 JPQL {@code select new} 的构造器投影目标。
 */
public record ServiceBillStateCount(ServiceBillState state, Long count) {
}
