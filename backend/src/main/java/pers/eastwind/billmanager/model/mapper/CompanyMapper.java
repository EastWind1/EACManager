package pers.eastwind.billmanager.model.mapper;

import pers.eastwind.billmanager.model.dto.CompanyDTO;
import pers.eastwind.billmanager.model.entity.Company;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CompanyMapper {

    Company toCompany(CompanyDTO companyDTO);

    CompanyDTO toCompanyDTO(Company company);
}
