package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

// Sends the exercise group both ways on purpose, because which one Artemis reads depends on its version.
// Artemis 9 bound the full exercise entity here and took the nested object; Artemis 10 binds
// UpdateModelingExerciseDTO, which has a flat `exerciseGroupId` and ignores the nested one. Sending only the nested
// form against Artemis 10 leaves the group unset server-side and fails the run with "An exercise must have either a
// course or an exercise group". Both are safe to send together: Artemis disables
// FAIL_ON_UNKNOWN_PROPERTIES, so whichever field its version does not know is ignored rather than rejected.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseCreateDTO(
    String type,
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    ExerciseGroupRef exerciseGroup,
    Long exerciseGroupId
) {
    /**
     * Create a modeling exercise DTO pre-filled with default benchmarking values.
     *
     * @param title           the title of the exercise.
     * @param exerciseGroupId the id of the exercise group the exercise belongs to.
     * @return a new {@link ModelingExerciseCreateDTO} for benchmarking.
     */
    public static ModelingExerciseCreateDTO forBenchmarking(String title, Long exerciseGroupId) {
        return new ModelingExerciseCreateDTO(
            "modeling",
            title,
            1.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            new ExerciseGroupRef(exerciseGroupId),
            exerciseGroupId
        );
    }
}
