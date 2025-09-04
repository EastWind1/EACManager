package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.entity.Attachment;

/**
 * 附件 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttachmentMapper extends BaseMapper<Attachment, AttachmentDTO> {
}
