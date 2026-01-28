package pers.eastwind.billmanager.company.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.company.model.CompanyDTO;
import pers.eastwind.billmanager.company.service.CompanyService;

import java.util.List;

/**
 * 公司控制器
 */
@RestController
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public PageResult<CompanyDTO> getAll(QueryParam queryParam) {
        return companyService.findEnabled(queryParam);
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CompanyDTO create(@RequestBody CompanyDTO companyDTO) {
        return companyService.create(companyDTO);
    }
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CompanyDTO update(@RequestBody CompanyDTO companyDTO) {
        return companyService.update(companyDTO);
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public void delete(@PathVariable Integer id) {
        companyService.disable(id);
    }
}
