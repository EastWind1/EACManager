package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBillAssignee;

import java.util.Collection;
import java.util.List;

public interface ServiceBillAssigneeRepository extends BaseRepository<ServiceBillAssignee> {
    boolean existsByServiceBillIdAndUserId(Integer serviceBillId, Integer userId);

    List<ServiceBillAssignee> findByServiceBillId(Integer serviceBillId);

    List<ServiceBillAssignee> findByServiceBillIdIn(Collection<Integer> serviceBillIds);

    @Modifying
    @Query("delete from ServiceBillAssignee a where a.serviceBillId = :serviceBillId")
    void deleteByServiceBillId(@Param("serviceBillId") Integer serviceBillId);
}
