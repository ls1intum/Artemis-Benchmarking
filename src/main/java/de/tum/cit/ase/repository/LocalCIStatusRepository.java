package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.LocalCIStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalCIStatusRepository extends JpaRepository<LocalCIStatus, Long> {}
