package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.model.dto.ReimbursementDTO;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.entity.Reimbursement;

import java.util.List;

/**
 * 报销单 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReimburseMapper extends BaseMapper<Reimbursement, ReimbursementDTO> {

    @Override
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    ReimbursementDTO toBaseDTO(Reimbursement reimbursement);

    ReimbursementDTO toDTO(Reimbursement reimbursement, List<Attachment> attachments);
}
