package pers.eastwind.billmanager.repository;

import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.model.entity.ServiceBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
    @Query(value = "select EXTRACT(YEAR FROM s.processed_date), EXTRACT(MONTH FROM s.processed_date), sum(s.total_amount) " +
            "from service_bill s " +
            "where (s.state = 2 or s.state = 3) and s.processed_date is not null " +
            "and s.processed_date >= now() - interval '1 year' " +
            "group by EXTRACT(YEAR FROM s.processed_date), EXTRACT(MONTH FROM s.processed_date)", nativeQuery = true)
    List<Object[]> sumTotalAmountByMonth();
}
