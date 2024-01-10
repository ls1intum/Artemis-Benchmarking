package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.SimulationRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {
    @Query(value = "select run from SimulationRun run where run.status = :#{#status}")
    List<SimulationRun> findAllByStatus(@Param("status") SimulationRun.Status status);

    @Query(value = "select run from SimulationRun run where run.simulation.id = :#{#simulationId}")
    List<SimulationRun> findAllBySimulationId(@Param("simulationId") long simulationId);

    @Query(value = "select run from SimulationRun run left join fetch run.stats s left join fetch run.logMessages l where run.id = :#{#id}")
    Optional<SimulationRun> findByIdWithStatsAndLogMessages(long id);
}
