package pers.eastwind.billmanager.company.controller;

import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.company.model.CompanyDTO;
import pers.eastwind.billmanager.company.model.CompanyMapper;
import pers.eastwind.billmanager.company.service.CompanyService;

import java.util.List;

/**
 * 公司控制器
 */
@RestController
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyService companyService;
    private final CompanyMapper companyMapper;

    public CompanyController(CompanyService companyService, CompanyMapper companyMapper) {
        this.companyService = companyService;
        this.companyMapper = companyMapper;
    }

    @GetMapping
    public List<CompanyDTO> getAll() {
        return companyService.findEnabled().stream().map(companyMapper::toDTO).toList();
    }
    @PostMapping
    public CompanyDTO create(@RequestBody CompanyDTO companyDTO) {
        Company company = companyMapper.toEntity(companyDTO);
        return companyMapper.toDTO(companyService.create(company));
    }
    @PutMapping
    public CompanyDTO update(@RequestBody CompanyDTO companyDTO) {
        Company company = companyMapper.toEntity(companyDTO);
        return companyMapper.toDTO(companyService.update(company));
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        companyService.disable(id);
    }
}
