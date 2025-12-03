package pers.eastwind.billmanager.servicebill.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.common.model.BaseMapper;

import java.util.List;

/**
 * 服务单 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ServiceBillMapper extends BaseMapper<ServiceBill, ServiceBillDTO> {

    @Override
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    ServiceBillDTO toBaseDTO(ServiceBill serviceBill);

    ServiceBillDTO toDTO(ServiceBill serviceBill, List<Attachment> attachments);
}
