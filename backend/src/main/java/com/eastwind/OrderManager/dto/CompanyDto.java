package com.eastwind.OrderManager.dto;

import com.eastwind.OrderManager.entity.Company;
import lombok.Data;

@Data
public class CompanyDto {
    private int id;
    /**
     * 名称
     */
    private String name;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系人电话
     */
    private String contactPhone;

    /**
     * 地址
     */
    private String address;

    /**
     * 从实体转换为dto
     */
    public static CompanyDto convertFromCompany(Company company) {
        CompanyDto dto = new CompanyDto();
        dto.setId(company.getId());
        dto.setName(company.getName());
        dto.setContactName(company.getContactName());
        dto.setContactPhone(company.getContactPhone());
        dto.setAddress(company.getAddress());
        return dto;
    }

    /**
     * 从dto转换为实体
     */
    public static Company convertToCompany(CompanyDto dto) {
        Company company = new Company();
        company.setId(dto.getId());
        company.setName(dto.getName());
        company.setContactName(dto.getContactName());
        company.setContactPhone(dto.getContactPhone());
        company.setAddress(dto.getAddress());
        return company;
    }
}
