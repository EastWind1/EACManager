package pers.eastwind.billmanager.attach.permission;

import org.springframework.stereotype.Component;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.PermissionProvider;

import java.util.Arrays;
import java.util.List;

/**
 * 权限提供者
 */
@Component
public class AttachPermissionProvider implements PermissionProvider {
    @Override
    public List<PermissionDTO> getPermissions() {
        return Arrays.stream(AttachPermission.values()).map(AttachPermission::toDTO).toList();
    }
}
