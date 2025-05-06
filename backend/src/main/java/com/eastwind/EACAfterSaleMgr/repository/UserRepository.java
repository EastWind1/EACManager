package com.eastwind.EACAfterSaleMgr.repository;

import com.eastwind.EACAfterSaleMgr.model.entity.User;
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
