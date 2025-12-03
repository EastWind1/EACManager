package pers.eastwind.billmanager.attach.model;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.common.model.BaseMapper;

/**
 * 附件 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttachmentMapper extends BaseMapper<Attachment, AttachmentDTO> {
}
