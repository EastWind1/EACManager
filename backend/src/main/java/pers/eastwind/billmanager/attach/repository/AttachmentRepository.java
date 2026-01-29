package pers.eastwind.billmanager.attach.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.attach.model.BillType;
import pers.eastwind.billmanager.common.repository.BaseRepository;

import java.util.List;

/**
 * 附件 Repository
 */
@Repository
public interface AttachmentRepository extends BaseRepository<Attachment> {
    /**
     * 根据业务单ID和业务单类型获取附件
     *
     * @param billId   业务单ID
     * @param billType 业务单类型
     * @return 附件
     */
    @Query("select r.attach from  BillAttachRelation r " +
            "where r.billId = :billId and r.billType = :billType")
    List<Attachment> findByBill(Integer billId, BillType billType);
}
