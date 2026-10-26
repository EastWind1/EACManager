package pers.eastwind.billmanager.security.model;

/**
 * 安全模块自身权限
 */
public enum SecurityPermission implements Permission {
    QUERY("permission:query", "查看权限配置", 37),
    UPDATE("permission:update", "修改权限配置", 38);

    private final String code;
    private final String name;
    private final int sortOrder;

    SecurityPermission(String code, String name, int sortOrder) {
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
        return "security";
    }

    @Override
    public String getModuleName() {
        return "安全配置";
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