package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCreateDTO(
    String title,
    String shortName,
    String description,
    String semester,
    String studentGroupName,
    String teachingAssistantGroupName,
    String editorGroupName,
    String instructorGroupName,
    ZonedDateTime startDate,
    ZonedDateTime endDate,
    ZonedDateTime enrollmentStartDate,
    ZonedDateTime enrollmentEndDate,
    ZonedDateTime unenrollmentEndDate,
    boolean testCourse,
    Boolean onlineCourse,
    Language language,
    String defaultProgrammingLanguage,
    Integer maxComplaints,
    Integer maxTeamComplaints,
    int maxComplaintTimeDays,
    int maxRequestMoreFeedbackTimeDays,
    int maxComplaintTextLimit,
    int maxComplaintResponseTextLimit,
    String color,
    Boolean enrollmentEnabled,
    String enrollmentConfirmationMessage,
    boolean unenrollmentEnabled,
    boolean faqEnabled,
    boolean learningPathsEnabled,
    boolean studentCourseAnalyticsDashboardEnabled,
    Integer presentationScore,
    Integer maxPoints,
    Integer accuracyOfScores,
    boolean restrictedAthenaModulesAccess,
    String timeZone,
    String courseInformationSharingConfiguration
) {
    /**
     * Create a course DTO pre-filled with default benchmarking values.
     *
     * @param title     the title of the course.
     * @param shortName the short name of the course.
     * @return a new {@link CourseCreateDTO} for benchmarking.
     */
    public static CourseCreateDTO forBenchmarking(String title, String shortName) {
        return new CourseCreateDTO(
            title,
            shortName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            Language.ENGLISH,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            false,
            null,
            false,
            false,
            false,
            false,
            null,
            null,
            1,
            false,
            null,
            "COMMUNICATION_AND_MESSAGING"
        );
    }
}
