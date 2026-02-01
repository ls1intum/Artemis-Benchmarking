package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionCreateDTO(String text, String hint, String explanation, Boolean isCorrect) {
    public static AnswerOptionCreateDTO correct(String text) {
        return new AnswerOptionCreateDTO(text, null, null, Boolean.TRUE);
    }

    public static AnswerOptionCreateDTO incorrect(String text) {
        return new AnswerOptionCreateDTO(text, null, null, Boolean.FALSE);
    }
}
