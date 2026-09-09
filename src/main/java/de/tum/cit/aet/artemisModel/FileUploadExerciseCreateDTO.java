package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

// Sends the exercise group both ways on purpose, because which one Artemis reads depends on its version. Artemis 9
// bound the full exercise entity here and took the nested object; Artemis 10 binds FileUploadExerciseInputDTO, which
// declares both but validates the flat `exerciseGroupId`, so the nested one alone fails the run with "An exercise
// must have either a courseId or an exerciseGroupId". Both are safe to send together: Artemis disables
// FAIL_ON_UNKNOWN_PROPERTIES, so whichever field its version does not know is ignored rather than rejected.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseCreateDTO(
    String type,
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    ExerciseGroupRef exerciseGroup,
    Long exerciseGroupId,
    String filePattern
) {
    /**
     * Create a file upload exercise DTO pre-filled with default benchmarking values.
     *
     * @param title           the title of the exercise.
     * @param exerciseGroupId the id of the exercise group the exercise belongs to.
     * @param filePattern     the allowed file pattern for submissions.
     * @return a new {@link FileUploadExerciseCreateDTO} for benchmarking.
     */
    public static FileUploadExerciseCreateDTO forBenchmarking(String title, Long exerciseGroupId, String filePattern) {
        return new FileUploadExerciseCreateDTO(
            "file-upload",
            title,
            1.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            new ExerciseGroupRef(exerciseGroupId),
            exerciseGroupId,
            filePattern
        );
    }
}
