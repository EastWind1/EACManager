package pers.eastwind.billmanager.servicebill.model;

import org.mapstruct.*;
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

    @Override
    @Mapping(target = "productCompany", ignore = true) // MapStruct 默认逻辑是直接在关联对象上赋值，而非新建，这会导致触发 JPA 的上下文检测，误认为是要更改关联对象 ID
    void updateEntityFromDTO(ServiceBillDTO dto, @MappingTarget ServiceBill entity);

    ServiceBillDTO toDTO(ServiceBill serviceBill, List<Attachment> attachments);
}
