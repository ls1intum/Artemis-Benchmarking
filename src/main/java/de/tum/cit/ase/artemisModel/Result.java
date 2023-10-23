package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.ZonedDateTime;

public class Result extends DomainObject {

    private ZonedDateTime completionDate;
    private Boolean successful;
    private Double score;
    private Boolean rated;

    @JsonIgnoreProperties({ "results", "participation" })
    private Submission submission;

    private Participation participation;
    private User assessor;
    private AssessmentType assessmentType;
    private Boolean hasComplaint;
    private Boolean exampleResult;
    private Integer testCaseCount = 0;
    private Integer passedTestCaseCount = 0;
    private Integer codeIssueCount = 0;

    public ZonedDateTime getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
    }

    public Boolean getSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Boolean getRated() {
        return rated;
    }

    public void setRated(Boolean rated) {
        this.rated = rated;
    }

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public User getAssessor() {
        return assessor;
    }

    public void setAssessor(User assessor) {
        this.assessor = assessor;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public Boolean getHasComplaint() {
        return hasComplaint;
    }

    public void setHasComplaint(Boolean hasComplaint) {
        this.hasComplaint = hasComplaint;
    }

    public Boolean getExampleResult() {
        return exampleResult;
    }

    public void setExampleResult(Boolean exampleResult) {
        this.exampleResult = exampleResult;
    }

    public Integer getTestCaseCount() {
        return testCaseCount;
    }

    public void setTestCaseCount(Integer testCaseCount) {
        this.testCaseCount = testCaseCount;
    }

    public Integer getPassedTestCaseCount() {
        return passedTestCaseCount;
    }

    public void setPassedTestCaseCount(Integer passedTestCaseCount) {
        this.passedTestCaseCount = passedTestCaseCount;
    }

    public Integer getCodeIssueCount() {
        return codeIssueCount;
    }

    public void setCodeIssueCount(Integer codeIssueCount) {
        this.codeIssueCount = codeIssueCount;
    }
}
