package pers.eastwind.billmanager.reimburse.model;

/**
 * 报销单状态计数行，作为 JPQL {@code select new} 的构造器投影目标。
 */
public record ReimburseStateCount(ReimburseState state, Long count) {
}
