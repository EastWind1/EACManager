package pers.eastwind.billmanager.servicebill.repository;

import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;

/**
 * 服务单 Repository
 */
public interface ServiceBillRepository extends BaseRepository<ServiceBill> {
    boolean existsByNumber(String number);
}
