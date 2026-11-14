package pers.eastwind.billmanager.security.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.security.model.Account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends BaseRepository<Account> {
    Account findByUsername(String username);

    Account findByUsernameAndDisabled(String username, boolean disabled);

    Optional<Account> findByIdAndDisabledFalse(Integer id);

    Page<Account> findByDisabled(boolean disabled, Pageable pageable);

    List<Account> findByDisabledFalse();

    List<Account> findByIdIn(Collection<Integer> ids);

    @Query("""
            select distinct a from Account a, UserRole ur, Role r
            where ur.userId = a.id and ur.roleId = r.id
              and r.code = :roleCode and r.disabled = false and a.disabled = false
            order by a.id
            """)
    List<Account> findEnabledByRoleCode(@Param("roleCode") String roleCode);
}
