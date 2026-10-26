package pers.eastwind.billmanager.security.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.PermissionEntity;
import pers.eastwind.billmanager.security.model.PermissionProvider;
import pers.eastwind.billmanager.security.repository.PermissionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 启动时将各模块声明的权限同步到 sys_permission
 */
@Slf4j
@Component
@Order(0)
public class PermissionSyncService implements ApplicationRunner {
    private final List<PermissionProvider> permissionProviders;
    private final PermissionRepository permissionRepository;

    public PermissionSyncService(List<PermissionProvider> permissionProviders,
                                 PermissionRepository permissionRepository) {
        this.permissionProviders = permissionProviders;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        Map<String, PermissionDTO> permissions = new HashMap<>();
        for (PermissionProvider provider : permissionProviders) {
            for (PermissionDTO permission : provider.getPermissions()) {
                if (permissions.putIfAbsent(permission.getCode(), permission) != null) {
                    throw new IllegalStateException("重复的权限编码: " + permission.getCode());
                }
            }
        }
        if (permissions.isEmpty()) {
            log.warn("未发现任何模块权限定义");
            return;
        }

        Map<String, PermissionEntity> existing = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(PermissionEntity::getCode, Function.identity()));
        List<PermissionEntity> changed = new ArrayList<>();
        for (PermissionDTO definition : permissions.values()) {
            PermissionEntity permission = existing.get(definition.getCode());
            if (permission == null) {
                permission = new PermissionEntity();
                permission.setCode(definition.getCode());
            }
            permission.setModule(definition.getModule());
            permission.setModuleName(definition.getModuleName());
            permission.setName(definition.getName());
            permission.setSortOrder(definition.getSortOrder() == null ? 0 : definition.getSortOrder());
            changed.add(permission);
        }
        permissionRepository.saveAll(changed);
    }
}
