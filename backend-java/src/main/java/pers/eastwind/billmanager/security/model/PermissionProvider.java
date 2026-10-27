package pers.eastwind.billmanager.security.model;

import java.util.List;

public interface PermissionProvider {
    List<PermissionDTO> getPermissions();
}
