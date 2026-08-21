package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

// Text exercise creation binds `UpdateTextExerciseDTO`, which takes the group as a flat `exerciseGroupId`. Modeling
// and file upload still bind the full exercise entity and want a nested `exerciseGroup`, so the shape differs per
// endpoint: check the endpoint's parameter type. Sending the wrong one leaves both the course and the group unset
// server-side and is rejected with "An exercise must have either a course or an exercise group".
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseCreateDTO(
    String type,
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    Long exerciseGroupId
) {
    /**
     * Create a text exercise DTO pre-filled with default benchmarking values.
     *
     * @param title           the title of the exercise.
     * @param exerciseGroupId the id of the exercise group the exercise belongs to.
     * @return a new {@link TextExerciseCreateDTO} for benchmarking.
     */
    public static TextExerciseCreateDTO forBenchmarking(String title, Long exerciseGroupId) {
        return new TextExerciseCreateDTO(
            "text",
            title,
            1.0,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            exerciseGroupId
        );
    }
}
