package pers.eastwind.billmanager.servicebill.permission;

import org.springframework.stereotype.Component;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.PermissionProvider;

import java.util.Arrays;
import java.util.List;

/**
 * 权限提供者
 */
@Component
public class ServiceBillPermissionProvider implements PermissionProvider {
    @Override
    public List<PermissionDTO> getPermissions() {
        return Arrays.stream(ServiceBillPermission.values()).map(ServiceBillPermission::toDTO).toList();
    }
}
