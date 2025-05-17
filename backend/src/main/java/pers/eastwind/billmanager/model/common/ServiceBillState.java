package pers.eastwind.billmanager.model.common;

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
     * 完成
     */
    FINISHED
}
