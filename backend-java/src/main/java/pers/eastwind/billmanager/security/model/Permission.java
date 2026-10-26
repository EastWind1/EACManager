package pers.eastwind.billmanager.security.model;

/**
 * 权限
 */
public interface Permission {
    /**
     * 编码
     */
    String getCode();

    /**
     * 名称
     */
    String getName();

    /**
     * 模块编号
     */
    String getModule();

    /**
     * 模块名称
     */
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
