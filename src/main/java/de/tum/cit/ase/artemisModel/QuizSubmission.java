package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizSubmission extends Submission {

    private Long quizBatch;
    private Double scoreInPoints;
    private Set<SubmittedAnswer> submittedAnswers = new HashSet<>();

    public Long getQuizBatch() {
        return quizBatch;
    }

    public void setQuizBatch(Long quizBatch) {
        this.quizBatch = quizBatch;
    }

    public Double getScoreInPoints() {
        return scoreInPoints;
    }

    public void setScoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
    }

    public Set<SubmittedAnswer> getSubmittedAnswers() {
        return submittedAnswers;
    }

    public void setSubmittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
    }
}
