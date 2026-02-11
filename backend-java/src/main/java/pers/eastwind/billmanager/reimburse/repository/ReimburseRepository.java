package pers.eastwind.billmanager.reimburse.repository;

import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.reimburse.model.Reimbursement;

/**
 * 报销单 Repository
 */
@Repository
public interface ReimburseRepository extends BaseRepository<Reimbursement> {
    boolean existsByNumber(String number);
}
