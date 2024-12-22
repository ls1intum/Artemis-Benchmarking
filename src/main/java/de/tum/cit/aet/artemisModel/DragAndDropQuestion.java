package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropQuestion extends QuizQuestion {

    private String backgroundFilePath;
    private List<DropLocation> dropLocations = new ArrayList<>();
    private List<DragItem> dragItems = new ArrayList<>();
    private List<DragAndDropMapping> correctMappings = new ArrayList<>();

    public String getBackgroundFilePath() {
        return backgroundFilePath;
    }

    public void setBackgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = backgroundFilePath;
    }

    public List<DropLocation> getDropLocations() {
        return dropLocations;
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
    }

    public List<DragItem> getDragItems() {
        return dragItems;
    }

    public void setDragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems;
    }

    public List<DragAndDropMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<DragAndDropMapping> correctMappings) {
        this.correctMappings = correctMappings;
    }
}
