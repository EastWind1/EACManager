package pers.eastwind.billmanager.reimburse.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.reimburse.model.Reimburse;

import java.util.List;
import java.util.Optional;

/**
 * 报销单 Repository：抓取计划在此声明，列表不需要明细，详情与导出需要。
 */
public interface ReimburseRepository extends BaseRepository<Reimburse> {
    boolean existsByNumber(String number);

    /**
     * 按 ID 批量查询并带出明细，供导出使用
     */
    @Override
    @EntityGraph(attributePaths = "details")
    List<Reimburse> findAllById(Iterable<Integer> ids);

    @EntityGraph(attributePaths = "details")
    @Query("select r from Reimburse r where r.id = :id")
    Optional<Reimburse> findDetailById(@Param("id") Integer id);
}
