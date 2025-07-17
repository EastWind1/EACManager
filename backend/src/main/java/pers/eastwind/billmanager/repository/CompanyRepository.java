package pers.eastwind.billmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.model.entity.Company;

/**
 * 公司 DAO
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Integer> {
}
