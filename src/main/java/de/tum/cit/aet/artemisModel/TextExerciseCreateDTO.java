package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseCreateDTO(
    String type,
    String title,
    Double maxPoints,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    ExerciseGroupRef exerciseGroup
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
            new ExerciseGroupRef(exerciseGroupId)
        );
    }
}
