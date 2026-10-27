package pers.eastwind.billmanager.security.model;

public interface Permission {
    String getCode();

    String getName();

    String getModule();

    String getModuleName();

    /**
     * 排序号；同一模块内按此值排序
     */
    int getSortOrder();

    /**
     * 转换为权限 DTO，供模块注册和前端读取
     */
    default PermissionDTO toDTO() {
        return new PermissionDTO(null, getCode(), getModule(), getModuleName(), getName(), getSortOrder());
    }
}
