package de.tum.cit.aet.repository;

import de.tum.cit.aet.domain.SimulationStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationStatsRepository extends JpaRepository<SimulationStats, Long> {}
