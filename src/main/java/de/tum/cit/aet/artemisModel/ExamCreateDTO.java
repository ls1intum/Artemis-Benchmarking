package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamCreateDTO(
    String title,
    boolean testExam,
    ZonedDateTime visibleDate,
    ZonedDateTime startDate,
    ZonedDateTime endDate,
    Integer gracePeriod,
    int workingTime,
    Integer examMaxPoints,
    Boolean randomizeExerciseOrder,
    Integer numberOfExercisesInExam,
    Integer numberOfCorrectionRoundsInExam,
    CourseRef course
) {
    public static ExamCreateDTO forBenchmarking(
        String title,
        Long courseId,
        ZonedDateTime visibleDate,
        ZonedDateTime startDate,
        ZonedDateTime endDate
    ) {
        return new ExamCreateDTO(title, false, visibleDate, startDate, endDate, 180, 2 * 60 * 60, 4, false, 4, 1, new CourseRef(courseId));
    }
}
