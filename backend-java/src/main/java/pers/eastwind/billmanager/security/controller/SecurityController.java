package pers.eastwind.billmanager.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.service.AuthorizeService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final AuthorizeService authorizeService;

    public SecurityController(AuthorizeService authorizeService) {
        this.authorizeService = authorizeService;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.permission.SecurityPermission).QUERY.getCode())")
    public Map<String, List<PermissionDTO>> getPermissions() {
        return authorizeService.getPermissions();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('user:query', T(pers.eastwind.billmanager.security.permission.SecurityPermission).QUERY.getCode())")
    public List<RoleDTO> getRoles() {
        return authorizeService.getRoles();
    }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.permission.SecurityPermission).UPDATE.getCode())")
    public RoleDTO createRole(@RequestBody RoleDTO role) {
        return authorizeService.createRole(role);
    }

    @PutMapping("/role")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.permission.SecurityPermission).UPDATE.getCode())")
    public RoleDTO updateRole(@RequestBody RoleDTO role) {
        return authorizeService.updateRole(role);
    }

    @DeleteMapping("/role/{id}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.permission.SecurityPermission).UPDATE.getCode())")
    public void deleteRole(@PathVariable Integer id) {
        authorizeService.deleteRole(id);
    }

    @GetMapping("/role/{id}/permissions")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.permission.SecurityPermission).QUERY.getCode())")
    public List<PermissionDTO> getRolePermissions(@PathVariable Integer id) {
        return authorizeService.getRolePermissions(id);
    }

    @PutMapping("/role/{id}/permissions")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.security.permission.SecurityPermission).UPDATE.getCode())")
    public List<PermissionDTO> updateRolePermissions(@PathVariable Integer id, @RequestBody Set<Integer> permissionIds) {
        return authorizeService.updateRolePermissions(id, permissionIds);
    }
}
