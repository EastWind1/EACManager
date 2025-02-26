package com.eastwind.ElevatorACAfterSaleManager.repository;

import com.eastwind.ElevatorACAfterSaleManager.entity.ServiceBill;
import com.eastwind.ElevatorACAfterSaleManager.entity.ServiceBillState;
import com.eastwind.ElevatorACAfterSaleManager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceBillRepository extends JpaRepository<ServiceBill, Integer> {
    List<ServiceBill> findAllByState(ServiceBillState state);

    @Query("from ServiceBill bill join ServiceBillProcessorDetail processorDetail " +
            "where bill.state = ?1 and processorDetail.processUser = ?2")
    List<ServiceBill> findAllByStateAndProcessor(ServiceBillState state, User processor);
}
