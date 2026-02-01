package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupCreateDTO(String title, Boolean mandatory, ExamRef exam) {
    public static ExerciseGroupCreateDTO forBenchmarking(String title, Long examId) {
        return new ExerciseGroupCreateDTO(title, Boolean.TRUE, new ExamRef(examId));
    }
}
