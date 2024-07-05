package de.tum.cit.ase.service.artemis.util;

import de.tum.cit.ase.artemisModel.Course;
import java.util.Set;

public record CourseDashboardDTO(Course course, Set<ParticipationResultDTO> participationResults) {
    public record ParticipationResultDTO(Double score, Boolean rated, Long participationId) {}
}
