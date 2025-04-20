package com.eastwind.EACAfterSaleMgr.repository;

import com.eastwind.EACAfterSaleMgr.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 公司DAO
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Integer> {
}
