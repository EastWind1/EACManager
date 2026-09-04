package pers.eastwind.billmanager.reimburse.repository;

import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.reimburse.model.Reimbursement;

/**
 * 报销单 Repository
 */
public interface ReimburseRepository extends BaseRepository<Reimbursement> {
    boolean existsByNumber(String number);
}
