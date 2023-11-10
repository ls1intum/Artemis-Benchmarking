package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.SimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {}
