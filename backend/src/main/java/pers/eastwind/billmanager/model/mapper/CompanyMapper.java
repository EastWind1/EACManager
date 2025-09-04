package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.model.dto.CompanyDTO;
import pers.eastwind.billmanager.model.entity.Company;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CompanyMapper extends BaseMapper<Company, CompanyDTO> {
}
