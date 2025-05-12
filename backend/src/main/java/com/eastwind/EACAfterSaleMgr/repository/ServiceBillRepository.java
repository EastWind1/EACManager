package com.eastwind.EACAfterSaleMgr.repository;

import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBill;
import com.eastwind.EACAfterSaleMgr.model.common.ServiceBillState;
import com.eastwind.EACAfterSaleMgr.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 服务单 DAO
 */
@Repository
public interface ServiceBillRepository extends JpaRepository<ServiceBill, Integer>, JpaSpecificationExecutor<ServiceBill> {
    boolean existsByNumber(String number);
}
