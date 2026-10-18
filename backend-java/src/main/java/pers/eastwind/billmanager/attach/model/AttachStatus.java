package pers.eastwind.billmanager.attach.model;

/**
 * 附件状态
 */
public enum AttachStatus {
    /**
     * 临时态，由临时目录管理
     */
    TEMP,
    /**
     * 瞬时态，由定时任务清理
     */
    TRANSIENT,
    /**
     * 持久态
     */
    PERSISTENT
}
