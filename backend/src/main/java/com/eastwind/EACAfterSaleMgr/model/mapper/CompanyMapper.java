package com.eastwind.EACAfterSaleMgr.model.mapper;

import com.eastwind.EACAfterSaleMgr.model.dto.CompanyDTO;
import com.eastwind.EACAfterSaleMgr.model.entity.Company;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CompanyMapper {

    Company toCompany(CompanyDTO companyDTO);

    CompanyDTO toCompanyDTO(Company company);
}
