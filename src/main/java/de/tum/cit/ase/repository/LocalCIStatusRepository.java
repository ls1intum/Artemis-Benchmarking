package de.tum.cit.ase.repository;

import de.tum.cit.ase.domain.LocalCIStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalCIStatusRepository extends JpaRepository<LocalCIStatus, Long> {
    @Modifying
    @Transactional
    @Query(value = "delete from LocalCIStatus status where status.isFinished = false")
    void deleteAllNotFinished();
}
