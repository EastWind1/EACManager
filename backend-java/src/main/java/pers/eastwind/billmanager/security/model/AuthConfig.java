package pers.eastwind.billmanager.security.model;

import java.util.List;

public record AuthConfig(List<RoleDTO> roles, List<PermissionDTO> permissions) {
}
