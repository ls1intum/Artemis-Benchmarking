package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal projection of Artemis' {@code ParticipationManagementDTO} returned by
 * {@code GET api/exercise/exercises/{exerciseId}/participations/page}. We only need the
 * participation id to look up its submissions for CI-status tracking.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationManagementDTO(long participationId) {}
