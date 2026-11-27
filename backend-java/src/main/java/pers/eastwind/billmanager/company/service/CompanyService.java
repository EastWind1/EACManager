package pers.eastwind.billmanager.company.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.model.CompanyDTO;
import pers.eastwind.billmanager.company.model.CompanyMapper;
import pers.eastwind.billmanager.company.repository.CompanyRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class CompanyService {
    public static final String COMPANY_CACHE = "company";

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    public CompanyService(CompanyRepository companyRepository, CompanyMapper companyMapper) {
        this.companyRepository = companyRepository;
        this.companyMapper = companyMapper;
    }

    public PageResult<CompanyDTO> findEnabled(QueryParam queryParam) {
        return PageResult.fromPage(companyRepository.findByDisabled(false, queryParam.getPageable()), companyMapper::toDTO);
    }

    @CacheEvict(value = COMPANY_CACHE)
    @Transactional
    public CompanyDTO create(CompanyDTO company) {
        if (company.getName() == null || company.getName().isBlank()) {
            throw new BizException("公司名称不能为空");
        }
        Company entity = companyMapper.toEntity(company);
        entity.setDisabled(false);
        return companyMapper.toDTO(companyRepository.save(entity));
    }

    /**
     * 下拉候选只返回启用公司
     */
    @Cacheable(
            value = COMPANY_CACHE,
            key = "#name"
    )
    public List<CompanyDTO> findEnabledByName(String name) {
        return new ArrayList<>(companyRepository.findByDisabledFalseAndNameContains(name)
                .stream()
                .map(companyMapper::toDTO)
                .toList());
    }

    public Company findEntityById(Integer id) {
        if (id == null) {
            return null;
        }
        return companyRepository.findById(id).orElse(null);
    }
    @CacheEvict(value = COMPANY_CACHE)
    @Transactional
    public CompanyDTO update(CompanyDTO company) {
        if (company.getId() == null) {
            throw new BizException("公司 ID 不能为空");
        }
        Company existing = companyRepository.findById(company.getId())
                .orElseThrow(() -> new BizException("公司不存在: " + company.getName()));
        Company entity = companyMapper.toEntity(company);
        // 启用状态只由 disable 接口维护。
        entity.setDisabled(Boolean.TRUE.equals(existing.getDisabled()));
        return companyMapper.toDTO(companyRepository.save(entity));
    }
    @CacheEvict(value = COMPANY_CACHE)
    @Transactional
    public void disable(Integer id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BizException("公司不存在: " + id));
        company.setDisabled(true);
        companyRepository.save(company);
    }
}