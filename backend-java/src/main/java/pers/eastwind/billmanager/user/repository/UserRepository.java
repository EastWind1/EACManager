package pers.eastwind.billmanager.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends BaseRepository<User> {
    boolean existsUserByUsername(String username);

    User findByUsername(String username);

    User findByUsernameAndDisabled(String username, boolean disabled);

    Page<User> findByDisabled(boolean disabled, Pageable pageable);

    List<User> findByDisabledFalse();

    User findByIdAndDisabledFalse(Integer id);

    /**
     * 查询拥有指定启用角色的用户，按 id 升序；测试需要拿真实数据库身份时复用现成管理员
     */
    @Query("""
            select distinct u from User u, UserRole ur, Role r
            where ur.userId = u.id and ur.roleId = r.id
              and r.code = :roleCode and r.disabled = false and u.disabled = false
            order by u.id
            """)
    List<User> findEnabledByRoleCode(@Param("roleCode") String roleCode);
}
