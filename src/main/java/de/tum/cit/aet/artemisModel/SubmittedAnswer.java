package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
        @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswer.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropSubmittedAnswer.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswer.class, name = "short-answer")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SubmittedAnswer extends DomainObject {}
