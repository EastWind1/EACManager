package pers.eastwind.billmanager.model.common;

/**
 * 服务单据状态
 */
public enum ServiceBillState {
    /**
     * 新建
     */
    CREATED("新建"),
    /**
     * 进行中
     */
    PROCESSING("进行中"),
    /**
     * 处理完成
     */
    PROCESSED("处理完成"),
    /**
     * 完成
     */
    FINISHED("完成");

    private final String label;

    ServiceBillState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
