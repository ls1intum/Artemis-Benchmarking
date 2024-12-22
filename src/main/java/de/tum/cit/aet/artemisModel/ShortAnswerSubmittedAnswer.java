package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedAnswer extends SubmittedAnswer {

    private Set<ShortAnswerSubmittedText> submittedTexts = new HashSet<>();

    public Set<ShortAnswerSubmittedText> getSubmittedTexts() {
        return submittedTexts;
    }

    public void setSubmittedTexts(Set<ShortAnswerSubmittedText> submittedTexts) {
        this.submittedTexts = submittedTexts;
    }
}
