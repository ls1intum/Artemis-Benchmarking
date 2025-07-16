package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
// Annotation necessary to distinguish between concrete implementations of Submission when deserializing from JSON
// @formatter:off
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProgrammingSubmission.class, name = "programming"),
        @JsonSubTypes.Type(value = ModelingSubmission.class, name = "modeling"),
        @JsonSubTypes.Type(value = QuizSubmission.class, name = "quiz"),
        @JsonSubTypes.Type(value = TextSubmission.class, name = "text"),
        @JsonSubTypes.Type(value = FileUploadSubmission.class, name = "file-upload")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Submission extends DomainObject {

    private Boolean submitted;
    private SubmissionType type;
    private Boolean exampleSubmission;
    private Participation participation;
    private ZonedDateTime submissionDate;

    @JsonIgnore
    private Set<SubmissionVersion> versions = new HashSet<>();

    @JsonIgnoreProperties({ "submission", "participation" })
    private List<Result> results = new ArrayList<>();

    public Boolean getSubmitted() {
        return submitted;
    }

    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    public SubmissionType getType() {
        return type;
    }

    public void setType(SubmissionType type) {
        this.type = type;
    }

    public Boolean getExampleSubmission() {
        return exampleSubmission;
    }

    public void setExampleSubmission(Boolean exampleSubmission) {
        this.exampleSubmission = exampleSubmission;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Set<SubmissionVersion> getVersions() {
        return versions;
    }

    public void setVersions(Set<SubmissionVersion> versions) {
        this.versions = versions;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }
}
