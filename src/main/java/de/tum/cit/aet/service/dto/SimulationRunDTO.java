package de.tum.cit.aet.service.dto;

import de.tum.cit.aet.domain.CiStatus;
import de.tum.cit.aet.domain.SimulationRun;
import java.time.ZonedDateTime;

public record SimulationRunDTO(
    Long id,
    ZonedDateTime startDateTime,
    ZonedDateTime endDateTime,
    SimulationRun.Status status,
    CiStatus ciStatus
) {
    public SimulationRunDTO(SimulationRun run) {
        this(run.getId(), run.getStartDateTime(), run.getEndDateTime(), run.getStatus(), run.getCiStatus());
    }
}
