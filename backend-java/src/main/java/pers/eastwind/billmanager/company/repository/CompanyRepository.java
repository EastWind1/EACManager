package pers.eastwind.billmanager.company.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.company.model.Company;

import java.util.List;

/**
 * 公司 Repository
 */
@Repository
public interface CompanyRepository extends BaseRepository<Company> {
    Page<Company> findByIsDisabled(boolean isDisabled, Pageable pageable);
    List<Company> findByNameContains(String name);
}
