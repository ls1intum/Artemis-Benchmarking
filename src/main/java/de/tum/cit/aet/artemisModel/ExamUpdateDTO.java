package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUpdateDTO(
    Long id,
    String title,
    boolean testExam,
    ZonedDateTime visibleDate,
    ZonedDateTime startDate,
    ZonedDateTime endDate,
    ZonedDateTime publishResultsDate,
    ZonedDateTime examStudentReviewStart,
    ZonedDateTime examStudentReviewEnd,
    Integer gracePeriod,
    int workingTime,
    String startText,
    String endText,
    String confirmationStartText,
    String confirmationEndText,
    Integer examMaxPoints,
    Boolean randomizeExerciseOrder,
    Integer numberOfExercisesInExam,
    Integer numberOfCorrectionRoundsInExam,
    String examiner,
    String moduleNumber,
    String courseName,
    ZonedDateTime exampleSolutionPublicationDate,
    CourseRef course,
    String examArchivePath
) {
    public static ExamUpdateDTO fromExam(Exam exam) {
        Course course = exam.getCourse();
        CourseRef courseRef = course == null ? null : new CourseRef(course.getId());
        return new ExamUpdateDTO(
            exam.getId(),
            exam.getTitle(),
            exam.isTestExam(),
            exam.getVisibleDate(),
            exam.getStartDate(),
            exam.getEndDate(),
            exam.getPublishResultsDate(),
            exam.getExamStudentReviewStart(),
            exam.getExamStudentReviewEnd(),
            exam.getGracePeriod(),
            exam.getWorkingTime(),
            exam.getStartText(),
            exam.getEndText(),
            exam.getConfirmationStartText(),
            exam.getConfirmationEndText(),
            exam.getExamMaxPoints(),
            exam.getRandomizeExerciseOrder(),
            exam.getNumberOfExercisesInExam(),
            exam.getNumberOfCorrectionRoundsInExam(),
            exam.getExaminer(),
            exam.getModuleNumber(),
            exam.getCourseName(),
            exam.getExampleSolutionPublicationDate(),
            courseRef,
            exam.getExamArchivePath()
        );
    }
}
