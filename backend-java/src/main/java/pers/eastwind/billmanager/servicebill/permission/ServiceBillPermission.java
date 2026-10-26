package pers.eastwind.billmanager.servicebill.permission;

import pers.eastwind.billmanager.security.model.Permission;

/**
 * 服务单模块权限
 */
public enum ServiceBillPermission implements Permission {
    QUERY("serviceBill:query", "查询服务单", 9),
    DETAIL("serviceBill:detail", "查看服务单", 10),
    CREATE("serviceBill:create", "新增服务单", 11),
    UPDATE("serviceBill:update", "修改服务单", 12),
    DELETE("serviceBill:delete", "删除服务单", 13),
    IMPORT("serviceBill:import", "导入服务单", 14),
    EXPORT("serviceBill:export", "导出服务单", 15),
    PROCESS("serviceBill:process", "开始处理服务单", 16),
    PROCESSED("serviceBill:processed", "处理完成服务单", 17),
    FINISH("serviceBill:finish", "回款完成服务单", 18),
    CANCEL_PROCESS("serviceBill:cancel-process", "取消处理服务单", 19),
    CANCEL_PROCESSED("serviceBill:cancel-processed", "取消处理完成服务单", 20),
    CANCEL_FINISH("serviceBill:cancel-finish", "取消回款完成服务单", 21),
    STATISTIC("serviceBill:statistic", "服务单统计", 22);

    private final String code;
    private final String name;
    private final int sortOrder;

    ServiceBillPermission(String code, String name, int sortOrder) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getModule() {
        return "serviceBill";
    }

    @Override
    public String getModuleName() {
        return "服务单";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getSortOrder() {
        return sortOrder;
    }
}