package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocation extends DomainObject {

    private Double posX;
    private Double posY;
    private Double width;
    private Double height;
    private Boolean invalid = false;

    @JsonIgnore
    private DragAndDropQuestion question;

    @JsonIgnore
    private Set<DragAndDropMapping> mappings = new HashSet<>();

    public Double getPosX() {
        return posX;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
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
