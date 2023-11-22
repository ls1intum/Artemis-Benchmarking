package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.util.ArtemisServer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtemisUserRepository extends JpaRepository<ArtemisUser, Long> {
    @Query(value = "select user from ArtemisUser user where user.server = :#{#server}")
    List<ArtemisUser> findAllByServer(@Param("server") ArtemisServer server);

    @Query(value = "delete from ArtemisUser user where user.server = :#{#server}")
    void deleteByServer(@Param("server") ArtemisServer server);
}
