package pers.eastwind.billmanager.company.repository;

import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.company.model.Company;

import java.util.List;

/**
 * 公司 Repository
 */
@Repository
public interface CompanyRepository extends BaseRepository<Company> {
    List<Company> findByIsDisabled(Boolean isDisabled);
}
