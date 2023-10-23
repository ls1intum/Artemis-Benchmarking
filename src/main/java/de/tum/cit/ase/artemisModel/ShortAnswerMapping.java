package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerMapping extends DomainObject {

    private Integer shortAnswerSpotIndex;
    private Integer shortAnswerSolutionIndex;
    private Boolean invalid;
    private ShortAnswerSolution solution;
    private ShortAnswerSpot spot;

    @JsonIgnore
    private ShortAnswerQuestion question;

    public Integer getShortAnswerSpotIndex() {
        return shortAnswerSpotIndex;
    }

    public void setShortAnswerSpotIndex(Integer shortAnswerSpotIndex) {
        this.shortAnswerSpotIndex = shortAnswerSpotIndex;
    }

    public Integer getShortAnswerSolutionIndex() {
        return shortAnswerSolutionIndex;
    }

    public void setShortAnswerSolutionIndex(Integer shortAnswerSolutionIndex) {
        this.shortAnswerSolutionIndex = shortAnswerSolutionIndex;
    }

    public Boolean getInvalid() {
        return invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerSolution getSolution() {
        return solution;
    }

    public void setSolution(ShortAnswerSolution solution) {
        this.solution = solution;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public void setSpot(ShortAnswerSpot spot) {
        this.spot = spot;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ShortAnswerQuestion question) {
        this.question = question;
    }
}
