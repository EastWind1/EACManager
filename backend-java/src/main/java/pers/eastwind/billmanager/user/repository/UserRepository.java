package pers.eastwind.billmanager.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.user.model.User;

/**
 * 用户 Repository
 */
public interface UserRepository extends BaseRepository<User> {
    boolean existsUserByUsername(String username);

    User findByUsername(String username);

    User findByUsernameAndDisabled(String username, boolean disabled);
    
    Page<User> findByDisabled(boolean disabled, Pageable pageable);
}
