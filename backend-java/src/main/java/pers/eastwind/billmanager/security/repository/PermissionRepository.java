package pers.eastwind.billmanager.security.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.security.model.PermissionEntity;

import java.util.Collection;
import java.util.List;

/**
 * 权限类型仓库
 */
public interface PermissionRepository extends JpaRepository<PermissionEntity, Integer> {
    List<PermissionEntity> findAllByOrderBySortOrderAsc();

    List<PermissionEntity> findByIdIn(Collection<Integer> ids);

    List<PermissionEntity> findByCodeIn(Collection<String> codes);

    PermissionEntity findByCode(String code);

    @Query("""
                select distinct p
                from RolePermission rp, PermissionEntity p
                where rp.permissionId = p.id
                    and rp.roleId in :roleIds
                order by p.sortOrder, p.code
            """)
    List<PermissionEntity> findByRoleIds(Collection<Integer> roleIds);
}
