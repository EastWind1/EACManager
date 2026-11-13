package pers.eastwind.billmanager.security.permission;

import org.springframework.stereotype.Component;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.PermissionProvider;

import java.util.Arrays;
import java.util.List;

@Component
public class SecurityPermissionProvider implements PermissionProvider {
    @Override
    public List<PermissionDTO> getPermissions() {
        return Arrays.stream(SecurityPermission.values()).map(SecurityPermission::toDTO).toList();
    }
}
