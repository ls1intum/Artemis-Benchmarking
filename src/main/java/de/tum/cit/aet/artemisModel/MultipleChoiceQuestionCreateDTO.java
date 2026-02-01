package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionCreateDTO(
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
    public static MultipleChoiceQuestionCreateDTO forBenchmarking(
        String title,
        String text,
        Double points,
        List<AnswerOptionCreateDTO> answerOptions
    ) {
        return new MultipleChoiceQuestionCreateDTO(
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
