package pers.eastwind.billmanager.repository;

import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.model.entity.ServiceBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 服务单 DAO
 */
@Repository
public interface ServiceBillRepository extends JpaRepository<ServiceBill, Integer>, JpaSpecificationExecutor<ServiceBill> {
    boolean existsByNumber(String number);
    /**
     * 按状态分组统计单据数量
     */
    @Query("SELECT s.state, COUNT(s) FROM ServiceBill s GROUP BY s.state")
    List<Object[]> countByState();
    /**
     * 按完工日期（processedDate）的年月分组，统计非新建状态的单据金额总和
     */
    @Query(value = "select year(s.processedDate), month(s.processedDate), sum(s.totalAmount) " +
            "from ServiceBill s " +
            "where (s.state = 2 or s.state = 3) and s.processedDate is not null " +
            "and s.processedDate between :start and :end "+
            "group by year(s.processedDate), month(s.processedDate)")
    List<Object[]> sumTotalAmountByMonth(Instant start,  Instant end);
}
