package pers.eastwind.billmanager.security.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.security.model.Role;
import pers.eastwind.billmanager.security.model.RolePermissionRow;

import java.util.Collection;
import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    boolean existsByCode(String code);

    Role findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.code = :code")
    Role findByCodeForUpdate(@Param("code") String code);

    List<Role> findAllByOrderBySortOrderAscIdAsc();

    List<Role> findByIdIn(Collection<Integer> ids);

    List<Role> findByCodeIn(Collection<String> codes);

    @Query("""
            select r from UserRole ur, Role r
            where ur.roleId = r.id
            and r.disabled = false
            and ur.userId = :userId
            order by r.sortOrder, r.id
            """)
    List<Role> findEnabledByUserId(Integer userId);

    /**
     * 一次查出多个角色的权限对照行，供批量组装用户权限使用
     */
    @Query("""
            select new pers.eastwind.billmanager.security.model.RolePermissionRow(rp.roleId, p)
            from RolePermission rp, PermissionEntity p
            where rp.permissionId = p.id
              and rp.roleId in :roleIds
            order by p.sortOrder, p.code
            """)
    List<RolePermissionRow> findPermissionsByRoleIds(@Param("roleIds") Collection<Integer> roleIds);
}
