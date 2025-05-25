package de.tum.cit.aet.repository;

import de.tum.cit.aet.domain.CiStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CiStatusRepository extends JpaRepository<CiStatus, Long> {
    @Modifying
    @Transactional
    @Query(value = "delete from CiStatus status where status.isFinished = false")
    void deleteAllNotFinished();

    CiStatus findBySimulationRunId(Long simulationRunId);
}
