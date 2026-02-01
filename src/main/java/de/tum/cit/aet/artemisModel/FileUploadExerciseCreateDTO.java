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
