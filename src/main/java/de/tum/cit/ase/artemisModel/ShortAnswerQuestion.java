package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerQuestion extends QuizQuestion {

    private List<ShortAnswerSpot> spots = new ArrayList<>();
    private List<ShortAnswerSolution> solutions = new ArrayList<>();
    private List<ShortAnswerMapping> correctMappings = new ArrayList<>();
    private Integer similarityValue = 85;
    private Boolean matchLetterCase = false;

    public List<ShortAnswerSpot> getSpots() {
        return spots;
    }

    public void setSpots(List<ShortAnswerSpot> spots) {
        this.spots = spots;
    }

    public List<ShortAnswerSolution> getSolutions() {
        return solutions;
    }

    public void setSolutions(List<ShortAnswerSolution> solutions) {
        this.solutions = solutions;
    }

    public List<ShortAnswerMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<ShortAnswerMapping> correctMappings) {
        this.correctMappings = correctMappings;
    }

    public Integer getSimilarityValue() {
        return similarityValue;
    }

    public void setSimilarityValue(Integer similarityValue) {
        this.similarityValue = similarityValue;
    }

    public Boolean getMatchLetterCase() {
        return matchLetterCase;
    }

    public void setMatchLetterCase(Boolean matchLetterCase) {
        this.matchLetterCase = matchLetterCase;
    }
}
