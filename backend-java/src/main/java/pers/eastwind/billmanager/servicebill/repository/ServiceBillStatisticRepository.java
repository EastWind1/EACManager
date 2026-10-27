package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pers.eastwind.billmanager.common.model.MonthAmountRow;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.servicebill.model.ServiceBillStateCount;

import java.time.Instant;
import java.util.List;

/**
 * 服务单统计 Repository：查询统一用 JPQL 构造器投影成 record，不用 {@code Object[]} 手工拆箱。
 */
public interface ServiceBillStatisticRepository extends Repository<ServiceBill, Integer> {

    @Query("""
            select new pers.eastwind.billmanager.servicebill.model.ServiceBillStateCount(s.state, count(s))
            from ServiceBill s
            group by s.state
            """)
    List<ServiceBillStateCount> countByState();

    /**
     * 按状态条件按完工日期的年月分组，统计金额总和。
     */
    @Query("""
            select new pers.eastwind.billmanager.common.model.MonthAmountRow(
                year(s.processedDate), month(s.processedDate), sum(s.totalAmount))
            from ServiceBill s
            where s.state in :states
              and s.processedDate is not null
              and s.processedDate between :start and :end
            group by year(s.processedDate), month(s.processedDate)
            """)
    List<MonthAmountRow> sumAmountByStateGroupByMonth(List<ServiceBillState> states, Instant start, Instant end);
}
