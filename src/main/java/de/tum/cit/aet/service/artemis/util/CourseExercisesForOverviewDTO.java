package de.tum.cit.aet.service.artemis.util;

import java.util.Set;

/**
 * Subset of the Artemis {@code GET courses/{courseId}/exercises-for-overview} response that the simulation needs.
 *
 * <p>This is where the participation results live after Artemis PR #12999 split the course overview per tab; they used
 * to arrive with the whole course from the now deprecated {@code for-dashboard} endpoint.
 *
 * @param participationResults the results of the student's participations in this course
 */
public record CourseExercisesForOverviewDTO(Set<ParticipationResultDTO> participationResults) {
    public record ParticipationResultDTO(Double score, Boolean rated, Long participationId) {}
}
