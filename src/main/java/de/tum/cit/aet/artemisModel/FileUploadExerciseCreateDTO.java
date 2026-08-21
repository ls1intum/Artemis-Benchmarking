package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

// This endpoint still binds the full exercise entity, so the group goes in as a nested object. Only text exercise
// creation moved to a DTO with a flat `exerciseGroupId`; check the endpoint's parameter type rather than assuming,
// because sending the wrong shape leaves the group unset server-side and yields "An exercise must have either a
// course or an exercise group".
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
