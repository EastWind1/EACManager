package pers.eastwind.billmanager.attach.permission;

import pers.eastwind.billmanager.security.model.Permission;

/**
 * 附件模块权限
 */
public enum AttachPermission implements Permission {
    DOWNLOAD("attach:download", "下载附件", 1),
    UPLOAD("attach:upload", "上传附件", 2);

    private final String code;
    private final String name;
    private final int sortOrder;

    AttachPermission(String code, String name, int sortOrder) {
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
        return "attach";
    }

    @Override
    public String getModuleName() {
        return "附件";
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