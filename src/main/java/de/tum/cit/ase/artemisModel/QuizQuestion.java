package de.tum.cit.ase.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
        @JsonSubTypes.Type(value = MultipleChoiceQuestion.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestion.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestion.class, name = "short-answer") }
)
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizQuestion extends DomainObject {}
