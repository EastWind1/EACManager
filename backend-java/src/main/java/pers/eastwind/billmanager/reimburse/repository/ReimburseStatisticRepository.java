package pers.eastwind.billmanager.reimburse.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.reimburse.model.Reimbursement;

import java.time.Instant;
import java.util.List;

/**
 * 报销单统计 Repository
 */
@Repository
public interface ReimburseStatisticRepository extends org.springframework.data.repository.Repository<Reimbursement, Integer> {

    /**
     * 按状态分组统计报销单数量
     */
    @Query("SELECT r.state, COUNT(r) FROM Reimbursement r GROUP BY r.state")
    List<Object[]> countByState();

    /**
     * 按报销日期的年月分组，统计近一年报销金额总和
     */
    @Query("select year(r.reimburseDate), month(r.reimburseDate), sum(r.totalAmount) " +
            "from Reimbursement r " +
            "where r.reimburseDate is not null " +
            "and r.reimburseDate between :start and :end " +
            "group by year(r.reimburseDate), month(r.reimburseDate)")
    List<Object[]> sumAmountGroupByMonth(Instant start, Instant end);
}
