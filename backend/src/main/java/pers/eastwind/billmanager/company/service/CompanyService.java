package pers.eastwind.billmanager.company.service;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.model.CompanyDTO;
import pers.eastwind.billmanager.company.model.CompanyMapper;
import pers.eastwind.billmanager.company.repository.CompanyRepository;

import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    public CompanyService(CompanyRepository companyRepository, CompanyMapper companyMapper) {
        this.companyRepository = companyRepository;
        this.companyMapper = companyMapper;
    }
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public PageResult<CompanyDTO> findEnabled(QueryParam queryParam) {
        return PageResult.fromPage(companyRepository.findByIsDisabled(false, queryParam.getPageable()), companyMapper::toDTO);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CompanyDTO create(CompanyDTO company) {
        if (company.getName() == null || company.getName().isEmpty()) {
            throw new BizException("公司名称不能为空");
        }
        return companyMapper.toDTO(companyRepository.save(companyMapper.toEntity(company)));
    }
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public List<CompanyDTO> findByName(String name) {
        return companyRepository.findByNameContains(name).stream().map(companyMapper::toDTO).toList();
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CompanyDTO update(CompanyDTO company) {
        if (!companyRepository.existsById(company.getId())) {
            throw new BizException("公司不存在: " + company.getName());
        }
        return companyMapper.toDTO(companyRepository.save(companyMapper.toEntity(company)));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void disable(Integer id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BizException("公司不存在: " + id));
        company.setIsDisabled(true);
        companyRepository.save(company);
    }
}