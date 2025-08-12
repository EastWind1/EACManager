package pers.eastwind.billmanager.repository;

import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.model.entity.Reimbursement;

/**
 * 报销单 Repository
 */
@Repository
public interface ReimburseRepository extends BaseRepository<Reimbursement> {
    boolean existsByNumber(String number);
}
