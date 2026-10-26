package pers.eastwind.billmanager.security.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.security.model.UserRole;

import java.util.List;

/**
 * 用户角色仓库
 */
public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {
    void deleteByUserId(Integer userId);

    void deleteByRoleId(Integer roleId);

    List<UserRole> findByUserId(Integer userId);

    @Query("""
            select count(ur)
            from UserRole ur, Role r
            where ur.roleId = r.id and r.code = :roleCode and r.disabled = false
            """)
    long countByRoleCode(@Param("roleCode") String roleCode);
}
