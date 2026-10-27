package pers.eastwind.billmanager.security.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.security.model.UserRole;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {

    /**
     * 批量删除用户的全部角色关联；JPQL 批量删除，派生 deleteByUserId 会先逐条查出来再逐条删（N+1）
     */
    @Modifying
    @Query("delete from UserRole ur where ur.userId = :userId")
    int deleteByUserId(@Param("userId") Integer userId);

    @Modifying
    @Query("delete from UserRole ur where ur.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Integer roleId);

    List<UserRole> findByUserIdIn(Collection<Integer> userIds);

    /**
     * 统计拥有指定启用角色的用户数
     */
    @Query("""
            select count(ur)
            from UserRole ur, Role r
            where ur.roleId = r.id and r.code = :roleCode and r.disabled = false
            """)
    long countByRoleCode(@Param("roleCode") String roleCode);
}
