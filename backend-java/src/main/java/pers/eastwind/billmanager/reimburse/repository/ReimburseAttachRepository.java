package pers.eastwind.billmanager.reimburse.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.reimburse.model.ReimburseAttach;

import java.util.Collection;
import java.util.List;

public interface ReimburseAttachRepository extends BaseRepository<ReimburseAttach> {
    @Query("select a from Attach a, ReimburseAttach r " +
            "where a.id = r.attachId and r.reimburseId = :reimburseId")
    List<Attach> findAttachmentsByBillId(Integer reimburseId);

    @Modifying
    @Query("delete from ReimburseAttach r " +
            "where r.reimburseId = :reimburseId and r.attachId in :attachIds")
    void deleteByBillIdAndAttachIds(Integer reimburseId, Collection<Integer> attachIds);
}
