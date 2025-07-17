package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import pers.eastwind.billmanager.model.dto.ServiceBillDTO;
import pers.eastwind.billmanager.model.entity.ServiceBill;

/**
 * 服务单 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ServiceBillMapper {
    ServiceBill toServiceBill(ServiceBillDTO serviceBillDTO);

    ServiceBillDTO toServiceBillDTO(ServiceBill serviceBill);

    /**
     * 返回不包含关联子实体的 DTO
     */
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    ServiceBillDTO toBasicServiceBillDTO(ServiceBill serviceBill);

    /**
     * 根据 DTO 更新实体
     *
     * @param serviceBillDTO dto
     * @param serviceBill    实体
     */
    void updateEntityFromDTO(ServiceBillDTO serviceBillDTO, @MappingTarget ServiceBill serviceBill);
}
