package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.ScheduleSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleSubscriberRepository extends JpaRepository<ScheduleSubscriber, Long> {}
