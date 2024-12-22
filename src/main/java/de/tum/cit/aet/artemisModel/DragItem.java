package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragItem extends DomainObject {

    private String pictureFilePath;

    private String text;

    private Boolean invalid = false;

    @JsonIgnore
    private DragAndDropQuestion question;

    @JsonIgnore
    private Set<DragAndDropMapping> mappings = new HashSet<>();

    public String getPictureFilePath() {
        return pictureFilePath;
    }

    public void setPictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
    }

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

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public void setQuestion(DragAndDropQuestion question) {
        this.question = question;
    }

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public void setMappings(Set<DragAndDropMapping> mappings) {
        this.mappings = mappings;
    }
}
