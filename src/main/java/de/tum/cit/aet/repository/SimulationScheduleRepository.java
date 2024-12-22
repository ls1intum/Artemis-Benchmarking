package de.tum.cit.aet.repository;

import de.tum.cit.aet.domain.SimulationSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationScheduleRepository extends JpaRepository<SimulationSchedule, Long> {
    @Query(value = "select schedule from SimulationSchedule schedule where schedule.simulation.id = :#{#simulationId}")
    List<SimulationSchedule> findAllBySimulationId(long simulationId);
}
