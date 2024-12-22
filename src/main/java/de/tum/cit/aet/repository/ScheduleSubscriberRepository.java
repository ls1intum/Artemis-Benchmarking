package de.tum.cit.aet.repository;

import de.tum.cit.aet.domain.ScheduleSubscriber;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleSubscriberRepository extends JpaRepository<ScheduleSubscriber, Long> {
    @Query(value = "select subscriber from ScheduleSubscriber subscriber where subscriber.key = :#{#key}")
    Optional<ScheduleSubscriber> findByKey(@Param("key") String key);
}
