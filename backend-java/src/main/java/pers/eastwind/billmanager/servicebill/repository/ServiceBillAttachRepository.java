package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBillAttach;

import java.util.Collection;
import java.util.List;

public interface ServiceBillAttachRepository extends BaseRepository<ServiceBillAttach> {
    @Query("select a from Attach a, ServiceBillAttach r " +
            "where a.id = r.attachId and r.serviceBillId = :serviceBillId")
    List<Attach> findAttachmentsByBillId(@Param("serviceBillId") Integer serviceBillId);

    @Modifying
    @Query("delete from ServiceBillAttach r " +
            "where r.serviceBillId = :serviceBillId and r.attachId in :attachIds")
    void deleteByBillIdAndAttachIds(@Param("serviceBillId") Integer serviceBillId, @Param("attachIds") Collection<Integer> attachIds);
}
