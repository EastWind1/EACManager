package com.eastwind.EACAfterSaleMgr.model.mapper;

import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 服务单 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ServiceBillMapper {
    ServiceBill toServiceBill(ServiceBillDTO serviceBillDTO);

    ServiceBillDTO toServiceBillDTO(ServiceBill serviceBill);

    List<ServiceBillDTO> toServiceBillDTOs(List<ServiceBill> serviceBills);

    /**
     * 根据 DTO 更新实体
     * @param serviceBillDTO dto
     * @param serviceBill 实体
     */
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    void updateEntityFromDTO(ServiceBillDTO serviceBillDTO, @MappingTarget ServiceBill serviceBill);
}
