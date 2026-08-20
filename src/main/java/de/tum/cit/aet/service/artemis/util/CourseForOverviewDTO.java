package de.tum.cit.aet.service.artemis.util;

/**
 * Subset of the Artemis {@code GET courses/{courseId}/for-overview} response that the simulation needs.
 *
 * <p>Unknown properties are ignored by the configured object mapper, so only the fields the simulation reads are
 * declared here.
 *
 * @param id                                     the course id
 * @param title                                  the course title
 * @param courseInformationSharingConfiguration  which communication features are enabled for the course
 */
public record CourseForOverviewDTO(Long id, String title, String courseInformationSharingConfiguration) {}
