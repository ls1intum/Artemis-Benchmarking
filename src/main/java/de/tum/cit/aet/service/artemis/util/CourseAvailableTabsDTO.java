package de.tum.cit.aet.service.artemis.util;

/**
 * Subset of the Artemis {@code GET courses/{courseId}/available-tabs} response that the simulation needs.
 *
 * <p>Since Artemis PR #12999 this endpoint is the single source of truth for which course tabs exist. The simulation
 * reads {@code communication} to decide whether a student would see the communication tab at all.
 *
 * @param communication whether communication is enabled for the course
 */
public record CourseAvailableTabsDTO(boolean communication) {}
