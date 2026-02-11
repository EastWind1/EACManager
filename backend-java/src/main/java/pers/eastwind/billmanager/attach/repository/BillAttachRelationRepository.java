package pers.eastwind.billmanager.attach.repository;

import pers.eastwind.billmanager.attach.model.BillAttachRelation;
import pers.eastwind.billmanager.attach.model.BillType;
import pers.eastwind.billmanager.common.repository.BaseRepository;

import java.util.List;

/**
 * 单据附件关联关系 Repository
 */
public interface BillAttachRelationRepository extends BaseRepository<BillAttachRelation> {
    /**
     * 根据单据ID和单据类型获取
     *
     * @param billId   单据ID
     * @param billType 单据类型
     * @return 单据附件关联关系
     */
    List<BillAttachRelation> findByBillIdAndBillType(Integer billId, BillType billType);
}
