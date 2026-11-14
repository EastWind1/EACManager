package pers.eastwind.billmanager.user.repository;

import pers.eastwind.billmanager.common.repository.BaseRepository;
import pers.eastwind.billmanager.user.model.UserProfile;

import java.util.Collection;
import java.util.List;

public interface UserProfileRepository extends BaseRepository<UserProfile> {
    List<UserProfile> findByUserIdIn(Collection<Integer> userIds);
}
