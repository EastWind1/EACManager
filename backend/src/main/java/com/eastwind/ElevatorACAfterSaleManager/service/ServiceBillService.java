package com.eastwind.ElevatorACAfterSaleManager.service;

import com.eastwind.ElevatorACAfterSaleManager.entity.ServiceBill;
import com.eastwind.ElevatorACAfterSaleManager.entity.ServiceBillProcessorDetail;
import com.eastwind.ElevatorACAfterSaleManager.entity.ServiceBillState;
import com.eastwind.ElevatorACAfterSaleManager.entity.User;
import com.eastwind.ElevatorACAfterSaleManager.repository.ServiceBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServiceBillService {
    private final TransactionTemplate transactionTemplate;
    private final ServiceBillRepository serviceBillRepository;

    public ServiceBillService(TransactionTemplate transactionTemplate, ServiceBillRepository serviceBillRepository) {
        this.transactionTemplate = transactionTemplate;
        this.serviceBillRepository = serviceBillRepository;
    }

    public ServiceBill create(ServiceBill serviceBill) {
        return serviceBillRepository.save(serviceBill);
    }

    public ServiceBill update(ServiceBill serviceBill) {
        return serviceBillRepository.save(serviceBill);
    }

    public void delete(ServiceBill serviceBill) {
        serviceBillRepository.delete(serviceBill);
    }

    public ServiceBill findById(int id) {
        return serviceBillRepository.findById(id).orElse(null);
    }

    public List<ServiceBill> findAllByStateAndProcessor(ServiceBillState state, User user) {
        return serviceBillRepository.findAllByStateAndProcessor(state, user);
    }

    public void allocateProcessor(int id, List<User> users) {
        ServiceBill bill = findById(id);
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
            processorDetail.setAcceptDate(OffsetDateTime.now());
            bill.getProcessDetails().add(processorDetail);
        }
        if (bill.getState() == ServiceBillState.CREATED) {
            bill.setState(ServiceBillState.PROCESSING);
        }
        transactionTemplate.execute(action -> serviceBillRepository.save(bill));
    }
}
