package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseCreateDTO(
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    String shortName,
    String packageName,
    Boolean allowOfflineIde,
    String programmingLanguage,
    String projectType,
    Boolean staticCodeAnalysisEnabled,
    ProgrammingExerciseBuildConfigDTO buildConfig,
    CourseRef course,
    ExerciseGroupRef exerciseGroup
) {
    public static ProgrammingExerciseCreateDTO forCourseBenchmarking(
        String title,
        Long courseId,
        String shortName,
        String packageName
    ) {
        return new ProgrammingExerciseCreateDTO(
            title,
            5.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            shortName,
            packageName,
            Boolean.TRUE,
            "JAVA",
            "PLAIN_GRADLE",
            Boolean.FALSE,
            ProgrammingExerciseBuildConfigDTO.forBenchmarking(),
            new CourseRef(courseId),
            null
        );
    }

    public static ProgrammingExerciseCreateDTO forExamBenchmarking(
        String title,
        Long exerciseGroupId,
        String shortName,
        String packageName
    ) {
        return new ProgrammingExerciseCreateDTO(
            title,
            1.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            shortName,
            packageName,
            Boolean.TRUE,
            "JAVA",
            "PLAIN_GRADLE",
            Boolean.FALSE,
            ProgrammingExerciseBuildConfigDTO.forBenchmarking(),
            null,
            new ExerciseGroupRef(exerciseGroupId)
        );
    }
}
