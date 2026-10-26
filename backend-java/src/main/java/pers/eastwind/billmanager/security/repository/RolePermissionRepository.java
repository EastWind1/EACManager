package pers.eastwind.billmanager.security.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.security.model.RolePermission;

import java.util.List;

/**
 * 角色权限仓库
 */

public interface RolePermissionRepository extends JpaRepository<RolePermission, Integer> {
    void deleteByRoleId(Integer id);

    @Query("""
            select exists(select 1 from RolePermission rp, Role r, PermissionEntity p
                where rp.roleId = r.id and rp.permissionId = p.id
                    and r.code = :roleCode and p.code = :permissionCode)
            """)
    boolean existsByRoleCodeAndPermissionCode(String roleCode, String permissionCode);

    List<RolePermission> findByRoleId(Integer roleId);
}
