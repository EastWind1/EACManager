package com.eastwind.EACAfterSaleMgr.service;

import com.eastwind.EACAfterSaleMgr.model.dto.ServiceBillDTO;
import com.eastwind.EACAfterSaleMgr.model.mapper.ServiceBillMapper;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBill;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBillProcessorDetail;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBillState;
import com.eastwind.EACAfterSaleMgr.model.entity.User;
import com.eastwind.EACAfterSaleMgr.repository.ServiceBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务单服务
 */
@Service
public class ServiceBillService {
    private final ServiceBillRepository serviceBillRepository;
    private final ServiceBillMapper serviceBillMapper;

    public ServiceBillService(ServiceBillRepository serviceBillRepository, ServiceBillMapper serviceBillMapper) {
        this.serviceBillRepository = serviceBillRepository;
        this.serviceBillMapper = serviceBillMapper;
    }

    @Transactional
    public ServiceBillDTO create(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() != null && serviceBillRepository.existsById(serviceBillDTO.getId())) {
            throw new RuntimeException("单据已存在");
        }
        if (serviceBillDTO.getNumber() == null || serviceBillDTO.getNumber().isEmpty()) {
            serviceBillDTO.setNumber(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + new SecureRandom().nextInt(1000));
        } else {
            if (serviceBillRepository.existsByNumber(serviceBillDTO.getNumber())) {
                throw new RuntimeException("单据编号已存在");
            }
        }
        return serviceBillMapper.toServiceBillDTO(serviceBillRepository.save(serviceBillMapper.toServiceBill(serviceBillDTO)));
    }
    @Transactional
    public ServiceBillDTO update(ServiceBillDTO serviceBillDTO) {
        if (serviceBillDTO.getId() == null) {
            throw new RuntimeException("id不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(serviceBillDTO.getId()).orElse(null);
        if (bill == null) {
            throw new RuntimeException("单据不存在");
        }
        serviceBillMapper.updateEntityFromDTO(serviceBillDTO, bill);
        return serviceBillMapper.toServiceBillDTO(serviceBillRepository.save(bill));
    }
    @Transactional
    public void deleteById(Integer id) {
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        serviceBillRepository.deleteById(id);
    }
    public ServiceBillDTO findById(int id) {
        return serviceBillMapper.toServiceBillDTO(serviceBillRepository.findById(id).orElse(null));
    }

    public List<ServiceBillDTO> findAll() {
        return serviceBillMapper.toServiceBillDTOs(serviceBillRepository.findAll());
    }

    public List<ServiceBillDTO> findAllByStateAndProcessor(ServiceBillState state, User user) {
        return serviceBillMapper.toServiceBillDTOs(serviceBillRepository.findAllByStateAndProcessor(state, user));
    }
    @Transactional
    public void allocateProcessor(Integer id, List<User> users) {
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        ServiceBill bill = serviceBillRepository.findById(id).orElse(null);
        if (bill == null) {
            throw new RuntimeException("未找到该单据");
        }

        if (bill.getState() != ServiceBillState.CREATED && bill.getState() != ServiceBillState.PROCESSING) {
            throw new RuntimeException("该单据非新建或处理中，无法分配");
        }


        if (bill.getProcessDetails() == null) {
            bill.setProcessDetails(new ArrayList<>());
        }
        for (ServiceBillProcessorDetail processDetail : bill.getProcessDetails()) {
            for (User user : users) {
                if (processDetail.getProcessUser().getId() == user.getId()) {
                    throw new RuntimeException("[" + user.getName() + "]正在处理该单据，无需再次分配");
                }
            }
        }
        for (User user : users) {
            ServiceBillProcessorDetail processorDetail = new ServiceBillProcessorDetail();
            processorDetail.setProcessUser(user);
            processorDetail.setAcceptDate(ZonedDateTime.now());
            bill.getProcessDetails().add(processorDetail);
        }
        if (bill.getState() == ServiceBillState.CREATED) {
            bill.setState(ServiceBillState.PROCESSING);
        }
        serviceBillRepository.save(bill);
    }
}
