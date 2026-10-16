package pers.eastwind.billmanager.reimburse.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.reimburse.model.ReimburseAttachRelation;

import java.util.Collection;
import java.util.List;

/**
 * 报销单附件关联 Repository
 */
public interface ReimburseAttachRelRepo extends BaseRepository<ReimburseAttachRelation> {
    /**
     * 根据报销单 ID 获取附件
     *
     * @param reimburseId 报销单 ID
     * @return 附件
     */
    @Query("select a from Attachment a, ReimburseAttachRelation r " +
            "where a.id = r.attachId and r.reimburseId = :reimburseId")
    List<Attachment> findAttachmentsByBillId(Integer reimburseId);

    /**
     * 删除报销单指定附件的关联记录
     *
     * @param reimburseId 报销单 ID
     * @param attachIds   附件 ID 集合
     */
    @Modifying
    @Query("delete from ReimburseAttachRelation r " +
            "where r.reimburseId = :reimburseId and r.attachId in :attachIds")
    void deleteByBillIdAndAttachIds(Integer reimburseId, Collection<Integer> attachIds);
}
