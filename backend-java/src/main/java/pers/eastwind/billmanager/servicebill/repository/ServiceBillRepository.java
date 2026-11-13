package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;

import java.util.List;
import java.util.Optional;

/**
 * 服务单 Repository：列表与详情各自的抓取计划在此声明，避免 EAGER 与 open-in-view 兜底。
 */
public interface ServiceBillRepository extends BaseRepository<ServiceBill> {
    boolean existsByNumber(String number);

    @Override
    @EntityGraph(attributePaths = "productCompany")
    Page<ServiceBill> findAll(Specification<ServiceBill> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "details")
    List<ServiceBill> findAllById(Iterable<Integer> ids);

    @EntityGraph(attributePaths = {"productCompany", "details"})
    @Query("select b from ServiceBill b where b.id = :id")
    Optional<ServiceBill> findDetailById(@Param("id") Integer id);
}
