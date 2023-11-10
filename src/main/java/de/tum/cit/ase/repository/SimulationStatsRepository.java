package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.SimulationStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationStatsRepository extends JpaRepository<SimulationStats, Long> {}
