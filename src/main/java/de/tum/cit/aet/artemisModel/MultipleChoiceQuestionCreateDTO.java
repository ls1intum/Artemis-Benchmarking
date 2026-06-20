package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionCreateDTO(
    String type,
    String title,
    String text,
    String hint,
    String explanation,
    Double points,
    ScoringType scoringType,
    Boolean randomizeOrder,
    List<AnswerOptionCreateDTO> answerOptions,
    Boolean singleChoice
) {
    /**
     * Create a multiple choice question DTO pre-filled with default benchmarking values.
     *
     * @param title         the title of the question.
     * @param text          the question text.
     * @param points        the points awarded for the question.
     * @param answerOptions the answer options of the question.
     * @return a new {@link MultipleChoiceQuestionCreateDTO} for benchmarking.
     */
    public static MultipleChoiceQuestionCreateDTO forBenchmarking(
        String title,
        String text,
        Double points,
        List<AnswerOptionCreateDTO> answerOptions
    ) {
        return new MultipleChoiceQuestionCreateDTO(
            "multiple-choice",
            title,
            text,
            null,
            null,
            points,
            ScoringType.ALL_OR_NOTHING,
            Boolean.FALSE,
            answerOptions,
            Boolean.TRUE
        );
    }
}
