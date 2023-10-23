package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentExam extends DomainObject {

    private Boolean submitted;
    private Integer workingTime;
    private Boolean started;
    private ZonedDateTime startedDate;
    private ZonedDateTime submissionDate;
    private Boolean testRun;
    private Exam exam;
    private User user;
    private List<Exercise> exercises = new ArrayList<>();
    private Set<ExamSession> examSessions = new HashSet<>();

    public Boolean getSubmitted() {
        return submitted;
    }

    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    public Integer getWorkingTime() {
        return workingTime;
    }

    public void setWorkingTime(Integer workingTime) {
        this.workingTime = workingTime;
    }

    public Boolean getStarted() {
        return started;
    }

    public void setStarted(Boolean started) {
        this.started = started;
    }

    public ZonedDateTime getStartedDate() {
        return startedDate;
    }

    public void setStartedDate(ZonedDateTime startedDate) {
        this.startedDate = startedDate;
    }

    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Boolean getTestRun() {
        return testRun;
    }

    public void setTestRun(Boolean testRun) {
        this.testRun = testRun;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    public Set<ExamSession> getExamSessions() {
        return examSessions;
    }

    public void setExamSessions(Set<ExamSession> examSessions) {
        this.examSessions = examSessions;
    }
}
