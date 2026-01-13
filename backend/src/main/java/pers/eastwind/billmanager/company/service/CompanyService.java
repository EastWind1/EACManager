package pers.eastwind.billmanager.company.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.repository.CompanyRepository;

import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    public List<Company> findEnabled() {
        return companyRepository.findByIsDisabled(false);
    }
    @Transactional
    public Company create(Company company) {
        if (company.getName() == null || company.getName().isEmpty()) {
            throw new BizException("公司名称不能为空");
        }
        return companyRepository.save(company);
    }

    @Transactional
    public Company update(Company company) {
        if (!companyRepository.existsById(company.getId())) {
            throw new BizException("公司不存在: " + company.getName());
        }
        return companyRepository.save(company);
    }

    @Transactional
    public void disable(Integer id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BizException("公司不存在: " + id));
        company.setIsDisabled(true);
        companyRepository.save(company);
    }
}