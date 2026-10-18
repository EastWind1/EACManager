package pers.eastwind.billmanager.attach.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachStatus;
import pers.eastwind.billmanager.common.repository.BaseRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 附件 Repository
 */
public interface AttachRepository extends BaseRepository<Attach> {
    /**
     * 持久化附件
     *
     * @param ids 附件 ID
     * @return 影响行数
     */
    @Modifying
    @Query("update Attach a set a.status = AttachStatus.PERSISTENT where a.id in :ids and a.status = AttachStatus.TRANSIENT")
    int persistByIds(Collection<Integer> ids);

    /**
     * 查找某状态下创建时间早于 date 的附件
     *
     * @param status 状态
     * @param date   创建时间上界
     * @return 附件
     */
    List<Attach> findByStatusAndCreatedDateBefore(AttachStatus status, Instant date);
}
