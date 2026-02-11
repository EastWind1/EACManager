package pers.eastwind.billmanager.company.model;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.common.model.BaseMapper;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CompanyMapper extends BaseMapper<Company, CompanyDTO> {
}
