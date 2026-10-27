package pers.eastwind.billmanager.attach.model;

public enum AttachStatus {
    /**
     * 由临时目录管理
     */
    TEMP,
    /**
     * 由定时任务清理
     */
    TRANSIENT,
    PERSISTENT
}
