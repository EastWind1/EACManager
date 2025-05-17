package pers.eastwind.billmanager.repository;

import pers.eastwind.billmanager.model.entity.ServiceBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 服务单 DAO
 */
@Repository
public interface ServiceBillRepository extends JpaRepository<ServiceBill, Integer>, JpaSpecificationExecutor<ServiceBill> {
    boolean existsByNumber(String number);
}
