package pers.eastwind.billmanager.reimburse.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pers.eastwind.billmanager.common.model.MonthAmountRow;
import pers.eastwind.billmanager.reimburse.model.Reimburse;
import pers.eastwind.billmanager.reimburse.model.ReimburseStateCount;

import java.time.Instant;
import java.util.List;

/**
 * 报销单统计 Repository：查询统一用 JPQL 构造器投影成 record，不用 {@code Object[]} 手工拆箱。
 */
public interface ReimburseStatisticRepository extends Repository<Reimburse, Integer> {

    @Query("""
            select new pers.eastwind.billmanager.reimburse.model.ReimburseStateCount(r.state, count(r))
            from Reimburse r
            group by r.state
            """)
    List<ReimburseStateCount> countByState();

    /**
     * 按报销日期的年月分组，统计近一年报销金额总和。
     */
    @Query("""
            select new pers.eastwind.billmanager.common.model.MonthAmountRow(
                year(r.reimburseDate), month(r.reimburseDate), sum(r.totalAmount))
            from Reimburse r
            where r.reimburseDate is not null
              and r.reimburseDate between :start and :end
            group by year(r.reimburseDate), month(r.reimburseDate)
            """)
    List<MonthAmountRow> sumAmountGroupByMonth(Instant start, Instant end);
}
