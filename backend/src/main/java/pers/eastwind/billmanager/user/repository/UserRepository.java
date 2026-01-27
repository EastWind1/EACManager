package pers.eastwind.billmanager.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.user.model.User;

/**
 * 用户 Repository
 */
@Repository
public interface UserRepository extends BaseRepository<User> {
    boolean existsUserByUsername(String username);

    User findByUsername(String username);


    Page<User> findByIsEnabled(boolean isEnabled, Pageable pageable);
}
