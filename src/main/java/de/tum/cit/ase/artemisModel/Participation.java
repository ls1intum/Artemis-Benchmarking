package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of Exercise when deserializing from JSON
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = StudentParticipation.class, name = "student"),
        @JsonSubTypes.Type(value = ProgrammingExerciseStudentParticipation.class, name = "programming"),
        @JsonSubTypes.Type(value = TemplateProgrammingExerciseParticipation.class, name = "template"),
        @JsonSubTypes.Type(value = SolutionProgrammingExerciseParticipation.class, name = "solution"),
    }
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Participation extends DomainObject {

    private InitializationState initializationState;

    private ZonedDateTime initializationDate;

    private ZonedDateTime individualDueDate;

    private Boolean testRun = false;

    @JsonIgnoreProperties("studentParticipations")
    protected Exercise exercise;

    private Set<Submission> submissions = new HashSet<>();

    public ZonedDateTime getInitializationDate() {
        return initializationDate;
    }

    public void setInitializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
    }

    public ZonedDateTime getIndividualDueDate() {
        return individualDueDate;
    }

    public void setIndividualDueDate(ZonedDateTime individualDueDate) {
        this.individualDueDate = individualDueDate;
    }

    public Boolean getTestRun() {
        return testRun;
    }

    public void setTestRun(Boolean testRun) {
        this.testRun = testRun;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Set<Submission> getSubmissions() {
        return submissions;
    }

    public void setSubmissions(Set<Submission> submissions) {
        this.submissions = submissions;
    }

    public InitializationState getInitializationState() {
        return initializationState;
    }

    public void setInitializationState(InitializationState initializationState) {
        this.initializationState = initializationState;
    }
}
