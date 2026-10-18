package pers.eastwind.billmanager.servicebill.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.servicebill.model.ServiceBillAttach;

import java.util.Collection;
import java.util.List;

/**
 * 服务单附件关联 Repository
 */
public interface ServiceBillAttachRepository extends BaseRepository<ServiceBillAttach> {
    /**
     * 根据服务单 ID 获取附件
     *
     * @param serviceBillId 服务单 ID
     * @return 附件
     */
    @Query("select a from Attach a, ServiceBillAttach r " +
            "where a.id = r.attachId and r.serviceBillId = :serviceBillId")
    List<Attach> findAttachmentsByBillId(@Param("serviceBillId") Integer serviceBillId);

    /**
     * 删除服务单指定附件的关联记录
     *
     * @param serviceBillId 服务单 ID
     * @param attachIds     附件 ID 集合
     */
    @Modifying
    @Query("delete from ServiceBillAttach r " +
            "where r.serviceBillId = :serviceBillId and r.attachId in :attachIds")
    void deleteByBillIdAndAttachIds(@Param("serviceBillId") Integer serviceBillId, @Param("attachIds") Collection<Integer> attachIds);
}
