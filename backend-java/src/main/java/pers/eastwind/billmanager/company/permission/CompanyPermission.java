package pers.eastwind.billmanager.company.permission;

import pers.eastwind.billmanager.security.model.Permission;

/**
 * 公司模块权限
 */
public enum CompanyPermission implements Permission {
    QUERY("company:query", "查看公司", 1),
    CREATE("company:create", "新增公司", 2),
    UPDATE("company:update", "修改公司", 3),
    DISABLE("company:disable", "禁用公司", 4);

    private final String code;
    private final String name;
    private final int sortOrder;

    CompanyPermission(String code, String name, int sortOrder) {
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
        return "company";
    }

    @Override
    public String getModuleName() {
        return "公司管理";
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