package pers.eastwind.billmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.model.entity.User;

import java.util.List;

/**
 * 用户 DAO
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsUserByUsername(String username);

    User findByUsername(String username);

    @Query("from User user where user.isEnabled = true")
    List<User> findAllEnabled();
}
