package de.tum.cit.ase.web.websocket.dto;

import java.time.Instant;

public class ExamLiveEventDto {

    private String createdBy;
    private Instant createdDate;
    private Long examId;
    private Long studentExamId;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public Long getStudentExamId() {
        return studentExamId;
    }

    public void setStudentExamId(Long studentExamId) {
        this.studentExamId = studentExamId;
    }

    @Override
    public String toString() {
        return (
            "ExamLiveEventDto{" +
            "createdBy='" +
            createdBy +
            '\'' +
            ", createdDate=" +
            createdDate +
            ", examId=" +
            examId +
            ", studentExamId=" +
            studentExamId +
            '}'
        );
    }
}
