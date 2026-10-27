package pers.eastwind.billmanager.company.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.company.model.CompanyDTO;
import pers.eastwind.billmanager.company.service.CompanyService;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.company.permission.CompanyPermission).QUERY.getCode())")
    public PageResult<CompanyDTO> getAll(QueryParam queryParam) {
        return companyService.findEnabled(queryParam);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.company.permission.CompanyPermission).CREATE.getCode())")
    public CompanyDTO create(@RequestBody CompanyDTO companyDTO) {
        return companyService.create(companyDTO);
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.company.permission.CompanyPermission).UPDATE.getCode())")
    public CompanyDTO update(@RequestBody CompanyDTO companyDTO) {
        return companyService.update(companyDTO);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.company.permission.CompanyPermission).DISABLE.getCode())")
    public void delete(@PathVariable Integer id) {
        companyService.disable(id);
    }
}
