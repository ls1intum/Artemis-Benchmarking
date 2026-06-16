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
    /**
     * Create a quiz exercise DTO pre-filled with default benchmarking values.
     *
     * @param title     the title of the exercise.
     * @param questions the multiple choice questions of the quiz.
     * @return a new {@link QuizExerciseCreateDTO} for benchmarking.
     */
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
