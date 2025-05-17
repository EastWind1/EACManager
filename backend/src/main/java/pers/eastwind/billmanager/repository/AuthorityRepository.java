package pers.eastwind.billmanager.repository;

import pers.eastwind.billmanager.model.entity.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 鉴权 DAO
 */
@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Integer> {
}
