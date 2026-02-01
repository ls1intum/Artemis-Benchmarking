package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseCreateDTO(
    String title,
    ExerciseMode mode,
    IncludedInOverallScore includedInOverallScore,
    QuizMode quizMode,
    Integer duration,
    Boolean randomizeQuestionOrder,
    List<MultipleChoiceQuestionCreateDTO> quizQuestions
) {
    public static QuizExerciseCreateDTO forBenchmarking(String title, List<MultipleChoiceQuestionCreateDTO> questions) {
        return new QuizExerciseCreateDTO(
            title,
            ExerciseMode.INDIVIDUAL,
            IncludedInOverallScore.INCLUDED_COMPLETELY,
            QuizMode.SYNCHRONIZED,
            null,
            Boolean.FALSE,
            questions
        );
    }
}
