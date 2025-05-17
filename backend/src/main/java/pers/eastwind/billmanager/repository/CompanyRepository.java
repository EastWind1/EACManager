package pers.eastwind.billmanager.repository;

import pers.eastwind.billmanager.model.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 公司 DAO
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Integer> {
}
