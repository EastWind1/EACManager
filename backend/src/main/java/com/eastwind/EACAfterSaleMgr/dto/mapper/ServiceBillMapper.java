package com.eastwind.EACAfterSaleMgr.dto.mapper;

import com.eastwind.EACAfterSaleMgr.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.entity.ServiceBill;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ServiceBillMapper {
    ServiceBillMapper INSTANCE = Mappers.getMapper(ServiceBillMapper.class);
    ServiceBill toServiceBill(ServiceBillDTO serviceBillDTO);
    ServiceBillDTO toServiceBillDTO(ServiceBill serviceBill);
    List<ServiceBillDTO> toServiceBillDTOs(List<ServiceBill> serviceBills);
}
