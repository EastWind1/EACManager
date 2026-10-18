package pers.eastwind.billmanager.reimburse.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.model.BaseMapper;

import java.util.List;

/**
 * 报销单 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReimburseMapper extends BaseMapper<Reimburse, ReimburseDTO> {

    @Override
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    ReimburseDTO toBaseDTO(Reimburse reimbursement);

    ReimburseDTO toDTO(Reimburse reimbursement, List<Attach> attachments);
}
