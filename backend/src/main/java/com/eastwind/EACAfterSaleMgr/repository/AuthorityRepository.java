package com.eastwind.EACAfterSaleMgr.repository;

import com.eastwind.EACAfterSaleMgr.model.entity.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 鉴权 DAO
 */
@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Integer> {
}
