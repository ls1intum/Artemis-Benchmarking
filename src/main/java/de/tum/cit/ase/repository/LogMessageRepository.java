package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.LogMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogMessageRepository extends JpaRepository<LogMessage, Long> {}
