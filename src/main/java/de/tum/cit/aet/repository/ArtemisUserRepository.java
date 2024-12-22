package de.tum.cit.aet.repository;

import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.util.ArtemisServer;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtemisUserRepository extends JpaRepository<ArtemisUser, Long> {
    @Query(value = "select user from ArtemisUser user where user.server = :#{#server}")
    List<ArtemisUser> findAllByServer(@Param("server") ArtemisServer server);

    @Query(value = "select user from ArtemisUser user where user.server = :#{#server} and user.serverWideId = :#{#serverWideId}")
    ArtemisUser findByServerAndServerWideId(@Param("server") ArtemisServer server, @Param("serverWideId") int serverWideId);

    @Modifying
    @Transactional
    @Query(value = "delete from ArtemisUser user where user.server = :#{#server} and not user.serverWideId = 0")
    void deleteByServer(@Param("server") ArtemisServer server);
}
