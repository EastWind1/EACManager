package com.eastwind.EACAfterSaleMgr.dto.mapper;

import com.eastwind.EACAfterSaleMgr.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.entity.ServiceBill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ServiceBillMapper {
    ServiceBillMapper INSTANCE = Mappers.getMapper(ServiceBillMapper.class);
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    ServiceBill toServiceBill(ServiceBillDTO serviceBillDTO);
    @Mapping(target = "createDate", ignore = true)
    ServiceBillDTO toServiceBillDTO(ServiceBill serviceBill);
}
