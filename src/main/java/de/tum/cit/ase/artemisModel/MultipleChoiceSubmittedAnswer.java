package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {

    private Set<AnswerOption> selectedOptions = new HashSet<>();

    public Set<AnswerOption> getSelectedOptions() {
        return selectedOptions;
    }

    public void setSelectedOptions(Set<AnswerOption> selectedOptions) {
        this.selectedOptions = selectedOptions;
    }
}
