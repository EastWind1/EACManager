package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;

/**
 * 服务单 Repository
 */
@Repository
public interface ServiceBillRepository extends BaseRepository<ServiceBill> {
    boolean existsByNumber(String number);
}
