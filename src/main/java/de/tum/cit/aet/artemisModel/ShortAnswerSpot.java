package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpot extends DomainObject {

    private Integer spotNr;
    private Integer width;
    private Boolean invalid;

    @JsonIgnore
    private ShortAnswerQuestion question;

    @JsonIgnore
    private Set<ShortAnswerMapping> mappings = new HashSet<>();

    public Integer getSpotNr() {
        return spotNr;
    }

    public void setSpotNr(Integer spotNr) {
        this.spotNr = spotNr;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
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
