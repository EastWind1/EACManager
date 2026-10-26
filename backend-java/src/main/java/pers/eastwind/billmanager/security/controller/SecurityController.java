package pers.eastwind.billmanager.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.service.SecurityService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与权限管理控制器
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * 获取全部可配置权限；分模块展示由前端完成
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.model.SecurityPermission).QUERY.getCode())")
    public Map<String, List<PermissionDTO>> getPermissions() {
        return securityService.getPermissions();
    }

    /**
     * 查询全部角色
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).QUERY.getCode(), T(pers.eastwind.billmanager.security.model.SecurityPermission).QUERY.getCode())")
    public List<RoleDTO> getRoles() {
        return securityService.getRoles();
    }

    /**
     * 创建角色
     */
    @PostMapping("/role")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.model.SecurityPermission).UPDATE.getCode())")
    public RoleDTO createRole(@RequestBody RoleDTO role) {
        return securityService.createRole(role);
    }

    /**
     * 修改角色
     */
    @PutMapping("/role")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.model.SecurityPermission).UPDATE.getCode())")
    public RoleDTO updateRole(@RequestBody RoleDTO role) {
        return securityService.updateRole(role);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/role/{id}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.model.SecurityPermission).UPDATE.getCode())")
    public void deleteRole(@PathVariable Integer id) {
        securityService.deleteRole(id);
    }

    /**
     * 查询角色权限
     */
    @GetMapping("/role/{id}/permissions")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.model.SecurityPermission).QUERY.getCode())")
    public List<PermissionDTO> getRolePermissions(@PathVariable Integer id) {
        return securityService.getRolePermissions(id);
    }

    /**
     * 更新角色权限
     */
    @PutMapping("/role/{id}/permissions")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.model.SecurityPermission).UPDATE.getCode())")
    public List<PermissionDTO> updateRolePermissions(@PathVariable Integer id, @RequestBody Set<Integer> permissionIds) {
        return securityService.updateRolePermissions(id, permissionIds);
    }
}
