package pers.eastwind.billmanager.reimburse.permission;

import pers.eastwind.billmanager.security.model.Permission;

public enum ReimbursePermission implements Permission {
    QUERY("reimburse:query", "查询报销单", 1),
    DETAIL("reimburse:detail", "查看报销单", 2),
    CREATE("reimburse:create", "新增报销单", 3),
    UPDATE("reimburse:update", "修改报销单", 4),
    DELETE("reimburse:delete", "删除报销单", 5),
    IMPORT("reimburse:import", "导入报销单", 6),
    EXPORT("reimburse:export", "导出报销单", 7),
    PROCESS("reimburse:process", "提交报销单", 8),
    FINISH("reimburse:finish", "完成报销单", 9),
    CANCEL_PROCESS("reimburse:cancel-process", "取消提交报销单", 10),
    CANCEL_FINISH("reimburse:cancel-finish", "取消完成报销单", 11),
    STATISTIC("reimburse:statistic", "报销单统计", 12);

    private final String code;
    private final String name;
    private final int sortOrder;

    ReimbursePermission(String code, String name, int sortOrder) {
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
        return "reimburse";
    }

    @Override
    public String getModuleName() {
        return "报销单";
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