package com.eastwind.EACAfterSaleMgr.model.entity;

/**
 * 服务单据状态
 */
public enum ServiceBillState {
    /**
     * 新建
     */
    CREATED,
    /**
     * 进行中
     */
    PROCESSING,
    /**
     * 处理完成
     */
    PROCESSED,
    /**
     * 待回款
     */
    REFUNDING,
    /**
     * 完成
     */
    FINISHED
}
