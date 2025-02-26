package com.eastwind.ElevatorACAfterSaleManager.dto.mapper;

import com.eastwind.ElevatorACAfterSaleManager.dto.CompanyDTO;
import com.eastwind.ElevatorACAfterSaleManager.entity.Company;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CompanyMapper {
    CompanyMapper INSTANCE = Mappers.getMapper(CompanyMapper.class);

    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    Company toCompany(CompanyDTO companyDTO);

    CompanyDTO toCompanyDTO(Company company);
}
