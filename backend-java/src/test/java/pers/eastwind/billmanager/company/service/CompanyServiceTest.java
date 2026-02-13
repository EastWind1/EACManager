package pers.eastwind.billmanager.company.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.company.model.CompanyDTO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompanyService 集成测试
 */
class CompanyServiceTest extends BaseServiceTest {

    @Autowired
    private CompanyService companyService;

    private CompanyDTO newCompany;

    @BeforeEach
    void setUp() {
        newCompany = new CompanyDTO();
        newCompany.setName("测试公司");
        newCompany.setContactName("联系人");
        newCompany.setContactPhone("13800138000");
        newCompany.setEmail("test@company.com");
        newCompany.setAddress("测试地址");
    }

    @Test
    @DisplayName("测试查询启用的公司列表")
    void shouldFindEnabledCompanies() {
        QueryParam queryParam = new QueryParam();
        queryParam.setPageIndex(0);
        queryParam.setPageSize(10);
        PageResult<CompanyDTO> result = companyService.findEnabled(queryParam);
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试创建公司")
    void shouldCreateCompany() {
        CompanyDTO createdCompany = companyService.create(newCompany);
        assertNotNull(createdCompany);
        assertNotNull(createdCompany.getId());
        assertEquals(newCompany.getName(), createdCompany.getName());
        assertEquals(newCompany.getContactName(), createdCompany.getContactName());
    }

    @Test
    @DisplayName("测试根据名称查找公司")
    void shouldFindCompanyByName() {
        companyService.create(newCompany);

        var list = companyService.findByName("测试");

        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(c -> c.getName().equals(newCompany.getName())));
    }

    @Test
    @DisplayName("测试更新公司")
    void shouldUpdateCompany() {
        CompanyDTO createdCompany = companyService.create(newCompany);
        createdCompany.setContactName("更新后的联系人");
        createdCompany.setContactPhone("13900139000");
        createdCompany.setAddress("更新后的地址");
        CompanyDTO updatedCompany = companyService.update(createdCompany);

        assertNotNull(updatedCompany);
        assertEquals("更新后的联系人", updatedCompany.getContactName());
        assertEquals("13900139000", updatedCompany.getContactPhone());
        assertEquals("更新后的地址", updatedCompany.getAddress());
    }

    @Test
    @DisplayName("测试禁用公司")
    void shouldDisableCompany() {
        CompanyDTO createdCompany = companyService.create(newCompany);
        companyService.disable(createdCompany.getId());
        var companies = companyService.findByName(createdCompany.getName());
        assertFalse(companies.isEmpty());
    }
}
