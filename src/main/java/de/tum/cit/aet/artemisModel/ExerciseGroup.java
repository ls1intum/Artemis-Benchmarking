package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseGroup extends DomainObject {

    private Exam exam;

    private String title;

    private Boolean mandatory;

    @JsonIgnoreProperties(value = "exerciseGroup", allowSetters = true)
    private Set<Exercise> exercises = new HashSet<>();

    public ExerciseGroup() {}

    public ExerciseGroup(String title, Boolean mandatory, Exam exam) {
        this.title = title;
        this.mandatory = mandatory;
        this.exam = exam;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getMandatory() {
        return mandatory;
    }

    public void setMandatory(Boolean mandatory) {
        this.mandatory = mandatory;
    }
}
