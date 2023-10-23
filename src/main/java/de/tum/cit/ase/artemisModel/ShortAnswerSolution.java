package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSolution extends DomainObject {

    private String text;
    private Boolean invalid = false;

    @JsonIgnore
    private ShortAnswerQuestion question;

    @JsonIgnore
    private Set<ShortAnswerMapping> mappings = new HashSet<>();

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getInvalid() {
        return invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ShortAnswerQuestion question) {
        this.question = question;
    }

    public Set<ShortAnswerMapping> getMappings() {
        return mappings;
    }

    public void setMappings(Set<ShortAnswerMapping> mappings) {
        this.mappings = mappings;
    }
}
