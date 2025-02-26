package com.eastwind.ElevatorACAfterSaleManager.repository;

import com.eastwind.ElevatorACAfterSaleManager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户DAO
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsUserByUsername(String username);
    User findByUsername(String username);
}
