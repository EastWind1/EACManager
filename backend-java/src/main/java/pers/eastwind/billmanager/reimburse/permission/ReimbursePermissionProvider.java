package pers.eastwind.billmanager.reimburse.permission;

import org.springframework.stereotype.Component;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.PermissionProvider;

import java.util.Arrays;
import java.util.List;

@Component
public class ReimbursePermissionProvider implements PermissionProvider {
    @Override
    public List<PermissionDTO> getPermissions() {
        return Arrays.stream(ReimbursePermission.values()).map(ReimbursePermission::toDTO).toList();
    }
}
