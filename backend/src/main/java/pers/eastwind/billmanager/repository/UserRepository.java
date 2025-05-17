package pers.eastwind.billmanager.repository;

import pers.eastwind.billmanager.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户 DAO
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsUserByUsername(String username);
    User findByUsername(String username);
}
