package pers.eastwind.billmanager.security.model;

/**
 * 角色与权限的对照行，作为批量组装权限的 JPQL 构造器投影目标，避免用 Object[] 取值
 */
public record RolePermissionRow(Integer roleId, PermissionEntity permission) {
}
