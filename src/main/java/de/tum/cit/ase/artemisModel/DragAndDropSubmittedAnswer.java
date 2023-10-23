package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropSubmittedAnswer extends SubmittedAnswer {

    private Set<DragAndDropMapping> mappings = new HashSet<>();

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public void setMappings(Set<DragAndDropMapping> mappings) {
        this.mappings = mappings;
    }
}
