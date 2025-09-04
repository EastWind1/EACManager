package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.model.dto.ServiceBillDTO;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.entity.ServiceBill;

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
