package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationRepository extends JpaRepository<Simulation, Long> {}
