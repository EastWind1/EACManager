package pers.eastwind.billmanager.repository;

import pers.eastwind.billmanager.model.common.BillType;
import pers.eastwind.billmanager.model.entity.BillAttachRelation;

import java.util.List;

/**
 * 单据附件关联关系 Repository
 */
public interface BillAttachRelationRepository extends BaseRepository<BillAttachRelation> {
    /**
     * 根据单据ID和单据类型获取
     *
     * @param billId     单据ID
     * @param billType   单据类型
     * @return 单据附件关联关系
     */
    List<BillAttachRelation> findByBillIdAndBillType(Integer billId, BillType billType);
}
