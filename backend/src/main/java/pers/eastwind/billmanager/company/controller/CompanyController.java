package pers.eastwind.billmanager.company.controller;

import org.springframework.web.bind.annotation.*;
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
    public List<CompanyDTO> getAll() {
        return companyService.findEnabled();
    }
    @PostMapping
    public CompanyDTO create(@RequestBody CompanyDTO companyDTO) {
        return companyService.create(companyDTO);
    }
    @PutMapping
    public CompanyDTO update(@RequestBody CompanyDTO companyDTO) {
        return companyService.update(companyDTO);
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        companyService.disable(id);
    }
}
