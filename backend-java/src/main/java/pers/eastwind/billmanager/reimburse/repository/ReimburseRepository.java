package pers.eastwind.billmanager.reimburse.repository;

import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.reimburse.model.Reimburse;

/**
 * 报销单 Repository
 */
public interface ReimburseRepository extends BaseRepository<Reimburse> {
    boolean existsByNumber(String number);
}
