package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropMapping extends DomainObject {

    private Integer dragItemIndex;

    private Integer dropLocationIndex;

    private Boolean invalid = false;

    private DragItem dragItem;

    private DropLocation dropLocation;

    @JsonIgnore
    private DragAndDropSubmittedAnswer submittedAnswer;

    @JsonIgnore
    private DragAndDropQuestion question;

    public Integer getDragItemIndex() {
        return dragItemIndex;
    }

    public void setDragItemIndex(Integer dragItemIndex) {
        this.dragItemIndex = dragItemIndex;
    }

    public Integer getDropLocationIndex() {
        return dropLocationIndex;
    }

    public void setDropLocationIndex(Integer dropLocationIndex) {
        this.dropLocationIndex = dropLocationIndex;
    }

    public Boolean getInvalid() {
        return invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public DragItem getDragItem() {
        return dragItem;
    }

    public void setDragItem(DragItem dragItem) {
        this.dragItem = dragItem;
    }

    public DropLocation getDropLocation() {
        return dropLocation;
    }

    public void setDropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
    }

    public DragAndDropSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(DragAndDropSubmittedAnswer submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public void setQuestion(DragAndDropQuestion question) {
        this.question = question;
    }
}
