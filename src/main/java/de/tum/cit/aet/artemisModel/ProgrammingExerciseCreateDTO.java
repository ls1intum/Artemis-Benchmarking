package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseCreateDTO(
    String type,
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    AssessmentType assessmentType,
    String shortName,
    String packageName,
    Boolean allowOnlineEditor,
    Boolean allowOfflineIde,
    Boolean allowOnlineIde,
    String programmingLanguage,
    String projectType,
    Boolean staticCodeAnalysisEnabled,
    Integer maxStaticCodeAnalysisPenalty,
    ProgrammingExerciseBuildConfigDTO buildConfig,
    CourseRef course,
    ExerciseGroupRef exerciseGroup
) {
    /**
     * Create a course programming exercise DTO pre-filled with default benchmarking values.
     *
     * @param title       the title of the exercise.
     * @param courseId    the id of the course the exercise belongs to.
     * @param shortName   the short name of the exercise.
     * @param packageName the package name of the exercise.
     * @return a new {@link ProgrammingExerciseCreateDTO} for benchmarking.
     */
    public static ProgrammingExerciseCreateDTO forCourseBenchmarking(String title, Long courseId, String shortName, String packageName) {
        return new ProgrammingExerciseCreateDTO(
            "programming",
            title,
            5.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            AssessmentType.AUTOMATIC,
            shortName,
            packageName,
            Boolean.TRUE,
            Boolean.TRUE,
            Boolean.FALSE,
            "JAVA",
            "PLAIN_GRADLE",
            Boolean.FALSE,
            null,
            ProgrammingExerciseBuildConfigDTO.forBenchmarking(),
            new CourseRef(courseId),
            null
        );
    }

    /**
     * Create an exam programming exercise DTO pre-filled with default benchmarking values.
     *
     * @param title           the title of the exercise.
     * @param exerciseGroupId the id of the exercise group the exercise belongs to.
     * @param shortName       the short name of the exercise.
     * @param packageName     the package name of the exercise.
     * @return a new {@link ProgrammingExerciseCreateDTO} for benchmarking.
     */
    public static ProgrammingExerciseCreateDTO forExamBenchmarking(
        String title,
        Long exerciseGroupId,
        String shortName,
        String packageName
    ) {
        return new ProgrammingExerciseCreateDTO(
            "programming",
            title,
            1.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            AssessmentType.AUTOMATIC,
            shortName,
            packageName,
            Boolean.TRUE,
            Boolean.TRUE,
            Boolean.FALSE,
            "JAVA",
            "PLAIN_GRADLE",
            Boolean.FALSE,
            null,
            ProgrammingExerciseBuildConfigDTO.forBenchmarking(),
            null,
            new ExerciseGroupRef(exerciseGroupId)
        );
    }
}
