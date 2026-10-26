package pers.eastwind.billmanager.user.permission;

import pers.eastwind.billmanager.security.model.Permission;

/**
 * 用户模块权限
 */
public enum UserPermission implements Permission {
    QUERY("user:query", "查看用户", 1),
    CREATE("user:create", "新增用户", 2),
    UPDATE("user:update", "修改用户", 3),
    DISABLE("user:disable", "禁用用户", 4);

    private final String code;
    private final String name;
    private final int sortOrder;

    UserPermission(String code, String name, int sortOrder) {
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
        return "user";
    }

    @Override
    public String getModuleName() {
        return "用户管理";
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