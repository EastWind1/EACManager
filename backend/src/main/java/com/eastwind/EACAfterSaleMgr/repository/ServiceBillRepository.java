package com.eastwind.EACAfterSaleMgr.repository;

import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBill;
import com.eastwind.EACAfterSaleMgr.model.common.ServiceBillState;
import com.eastwind.EACAfterSaleMgr.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 服务单 DAO
 */
@Repository
public interface ServiceBillRepository extends JpaRepository<ServiceBill, Integer> {
    List<ServiceBill> findAllByState(ServiceBillState state);

    @Query("from ServiceBill bill join ServiceBillProcessorDetail processorDetail " +
            "where bill.state = ?1 and processorDetail.processUser = ?2")
    List<ServiceBill> findAllByStateAndProcessor(ServiceBillState state, User processor);

    boolean existsById(int id);
    boolean existsByNumber(String number);
}
