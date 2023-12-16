package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.LogMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LogMessageRepository extends JpaRepository<LogMessage, Long> {
    @Query(value = "select log from LogMessage log where log.simulationRun.id = :#{#simulationRunId} and log.isError = true")
    List<LogMessage> findBySimulationRunIdAndErrorIsTrue(@Param("simulationRunId") long simulationRunId);
}
