package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedText extends DomainObject {

    private String text;
    private Boolean isCorrect;
    private ShortAnswerSpot spot;

    @JsonIgnore
    private ShortAnswerSubmittedAnswer submittedAnswer;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getCorrect() {
        return isCorrect;
    }

    public void setCorrect(Boolean correct) {
        isCorrect = correct;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public void setSpot(ShortAnswerSpot spot) {
        this.spot = spot;
    }

    public ShortAnswerSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(ShortAnswerSubmittedAnswer submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }
}
