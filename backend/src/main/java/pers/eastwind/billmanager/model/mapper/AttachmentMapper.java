package pers.eastwind.billmanager.model.mapper;

import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.entity.Attachment;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 附件 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AttachmentMapper {
    AttachmentDTO toAttachmentDTO(Attachment attachment);
    Attachment toAttachment(AttachmentDTO attachmentDTO);
}
