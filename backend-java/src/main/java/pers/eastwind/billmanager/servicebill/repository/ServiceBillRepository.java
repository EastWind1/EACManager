package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;

import java.time.Instant;
import java.util.List;

/**
 * 服务单 Repository
 */
@Repository
public interface ServiceBillRepository extends BaseRepository<ServiceBill> {
    boolean existsByNumber(String number);

    /**
     * 按状态分组统计单据数量
     */
    @Query("SELECT s.state, COUNT(s) FROM ServiceBill s GROUP BY s.state")
    List<Object[]> countByState();

    /**
     * 按状态条件、按完工日期（processedDate）的年月分组，统计单据金额总和
     */
    @Query(value = "select year(s.processedDate), month(s.processedDate), sum(s.totalAmount) " +
            "from ServiceBill s " +
            "where s.state in :states and s.processedDate is not null " +
            "and s.processedDate between :start and :end " +
            "group by year(s.processedDate), month(s.processedDate)")
    List<Object[]> sumAmountByStateGroupByMonth(List<ServiceBillState> states, Instant start, Instant end);
}
