package pers.eastwind.billmanager.security.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.security.model.RolePermission;

import java.util.Collection;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Integer> {

    /**
     * 批量删除角色的全部权限关联；JPQL 批量删除，派生 deleteByRoleId 会先逐条查出来再逐条删（N+1）
     */
    @Modifying
    @Query("delete from RolePermission rp where rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Integer id);

    @Query("""
            select exists(select 1 from RolePermission rp, Role r, PermissionEntity p
                where rp.roleId = r.id and rp.permissionId = p.id
                    and r.code = :roleCode and p.code = :permissionCode)
            """)
    boolean existsByRoleCodeAndPermissionCode(String roleCode, String permissionCode);

    List<RolePermission> findByRoleIdIn(Collection<Integer> roleIds);
}
