package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.SimulationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationScheduleRepository extends JpaRepository<SimulationSchedule, Long> {}
