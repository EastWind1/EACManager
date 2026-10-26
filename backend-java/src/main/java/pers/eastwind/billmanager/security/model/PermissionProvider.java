package pers.eastwind.billmanager.security.model;

import java.util.List;

/**
 * 模块权限提供者
 */
public interface PermissionProvider {
    List<PermissionDTO> getPermissions();
}
