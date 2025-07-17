package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import pers.eastwind.billmanager.model.dto.CompanyDTO;
import pers.eastwind.billmanager.model.entity.Company;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CompanyMapper {

    Company toCompany(CompanyDTO companyDTO);

    CompanyDTO toCompanyDTO(Company company);
}
