package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pers.eastwind.billmanager.model.dto.ReimbursementDTO;
import pers.eastwind.billmanager.model.entity.Reimbursement;

/**
 * 报销单 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReimburseMapper extends BaseMapper<Reimbursement, ReimbursementDTO> {

    @Override
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    ReimbursementDTO toBaseDTO(Reimbursement reimbursement);
}
