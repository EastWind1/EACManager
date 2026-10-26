package pers.eastwind.billmanager.security.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.security.model.Role;

import java.util.Collection;
import java.util.List;

/**
 * 角色仓库
 */
public interface RoleRepository extends JpaRepository<Role, Integer> {
    boolean existsByCode(String code);

    Role findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.code = :code")
    Role findByCodeForUpdate(@Param("code") String code);

    List<Role> findAllByOrderBySortOrderAscIdAsc();

    List<Role> findByIdIn(Collection<Integer> ids);

    @Query("""
            select r from UserRole ur, Role r
            where ur.roleId = r.id
            and r.disabled = false
            and ur.userId = :userId
            order by r.sortOrder, r.id
            """)
    List<Role> findEnabledByUserId(Integer userId);
}
