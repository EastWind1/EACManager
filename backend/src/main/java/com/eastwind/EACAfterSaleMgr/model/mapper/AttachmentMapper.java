package com.eastwind.EACAfterSaleMgr.model.mapper;

import com.eastwind.EACAfterSaleMgr.model.dto.AttachmentDTO;
import com.eastwind.EACAfterSaleMgr.model.entity.Attachment;
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
