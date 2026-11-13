package pers.eastwind.billmanager.servicebill.permission;

import pers.eastwind.billmanager.security.model.Permission;

public enum ServiceBillPermission implements Permission {
    QUERY("serviceBill:query", "查询服务单", 1),
    DETAIL("serviceBill:detail", "查看服务单", 2),
    CREATE("serviceBill:create", "新增服务单", 3),
    UPDATE("serviceBill:update", "修改服务单", 4),
    DELETE("serviceBill:delete", "删除服务单", 5),
    IMPORT("serviceBill:import", "导入服务单", 6),
    EXPORT("serviceBill:export", "导出服务单", 7),
    ASSIGN("serviceBill:assign", "指派服务单", 8),
    PROCESS("serviceBill:process", "开始处理服务单", 9),
    PROCESSED("serviceBill:processed", "处理完成服务单", 10),
    FINISH("serviceBill:finish", "回款完成服务单", 11),
    CANCEL_PROCESS("serviceBill:cancel-process", "取消处理服务单", 12),
    CANCEL_PROCESSED("serviceBill:cancel-processed", "取消处理完成服务单", 13),
    CANCEL_FINISH("serviceBill:cancel-finish", "取消回款完成服务单", 14),
    STATISTIC("serviceBill:statistic", "服务单统计", 15),
    // 拥有全部数据权限,否则只能操作自身创建和被指派的单据
    DATA_ALL("serviceBill:data-all", "管理全部服务单", 16);

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