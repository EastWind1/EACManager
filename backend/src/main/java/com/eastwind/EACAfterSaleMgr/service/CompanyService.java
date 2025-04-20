package com.eastwind.EACAfterSaleMgr.service;

import com.eastwind.EACAfterSaleMgr.entity.Company;
import com.eastwind.EACAfterSaleMgr.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    public Company create(Company company) {
        return companyRepository.save(company);
    }

    public Company update(Company company) {
        return companyRepository.save(company);
    }

    public void remove(Company company) {
        companyRepository.delete(company);
    }
}
