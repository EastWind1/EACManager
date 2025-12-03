package pers.eastwind.billmanager.company.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.repository.CompanyRepository;

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