package com.eastwind.OrderManager.repository;

import com.eastwind.OrderManager.entity.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 鉴权DAO
 */
@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Integer> {
}
