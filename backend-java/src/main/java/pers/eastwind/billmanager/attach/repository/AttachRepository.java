package pers.eastwind.billmanager.attach.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachStatus;
import pers.eastwind.billmanager.common.repository.BaseRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AttachRepository extends BaseRepository<Attach> {
    @Modifying
    @Query("update Attach a set a.status = AttachStatus.PERSISTENT where a.id in :ids and a.status = AttachStatus.TRANSIENT")
    int persistByIds(Collection<Integer> ids);

    List<Attach> findByStatusAndCreatedDateBefore(AttachStatus status, Instant date);
}
