package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseCreateDTO(
    String type,
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    ExerciseGroupRef exerciseGroup,
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
            filePattern
        );
    }
}
