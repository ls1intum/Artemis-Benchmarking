package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Exam extends DomainObject {

    private String title;
    private boolean testExam;
    private ZonedDateTime visibleDate;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime publishResultsDate;
    private ZonedDateTime examStudentReviewStart;
    private ZonedDateTime examStudentReviewEnd;
    private Integer gracePeriod = 180;
    private int workingTime;
    private String startText;
    private String endText;
    private String confirmationStartText;
    private String confirmationEndText;
    private Integer examMaxPoints;
    private Boolean randomizeExerciseOrder;
    private Integer numberOfExercisesInExam;
    private Integer numberOfCorrectionRoundsInExam;
    private String examiner;
    private String moduleNumber;
    private String courseName;
    private ZonedDateTime exampleSolutionPublicationDate;
    private Course course;

    @JsonIgnoreProperties(value = "exam", allowSetters = true)
    private List<ExerciseGroup> exerciseGroups = new ArrayList<>();

    @JsonIgnoreProperties("exam")
    private Set<StudentExam> studentExams = new HashSet<>();

    private String examArchivePath;

    @JsonIgnoreProperties("exam")
    private Set<ExamUser> examUsers = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isTestExam() {
        return testExam;
    }

    public void setTestExam(boolean testExam) {
        this.testExam = testExam;
    }

    public ZonedDateTime getVisibleDate() {
        return visibleDate;
    }

    public void setVisibleDate(ZonedDateTime visibleDate) {
        this.visibleDate = visibleDate;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public ZonedDateTime getPublishResultsDate() {
        return publishResultsDate;
    }

    public void setPublishResultsDate(ZonedDateTime publishResultsDate) {
        this.publishResultsDate = publishResultsDate;
    }

    public ZonedDateTime getExamStudentReviewStart() {
        return examStudentReviewStart;
    }

    public void setExamStudentReviewStart(ZonedDateTime examStudentReviewStart) {
        this.examStudentReviewStart = examStudentReviewStart;
    }

    public ZonedDateTime getExamStudentReviewEnd() {
        return examStudentReviewEnd;
    }

    public void setExamStudentReviewEnd(ZonedDateTime examStudentReviewEnd) {
        this.examStudentReviewEnd = examStudentReviewEnd;
    }

    public Integer getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(Integer gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public int getWorkingTime() {
        return workingTime;
    }

    public void setWorkingTime(int workingTime) {
        this.workingTime = workingTime;
    }

    public String getStartText() {
        return startText;
    }

    public void setStartText(String startText) {
        this.startText = startText;
    }

    public String getEndText() {
        return endText;
    }

    public void setEndText(String endText) {
        this.endText = endText;
    }

    public String getConfirmationStartText() {
        return confirmationStartText;
    }

    public void setConfirmationStartText(String confirmationStartText) {
        this.confirmationStartText = confirmationStartText;
    }

    public String getConfirmationEndText() {
        return confirmationEndText;
    }

    public void setConfirmationEndText(String confirmationEndText) {
        this.confirmationEndText = confirmationEndText;
    }

    public Integer getExamMaxPoints() {
        return examMaxPoints;
    }

    public void setExamMaxPoints(Integer examMaxPoints) {
        this.examMaxPoints = examMaxPoints;
    }

    public Boolean getRandomizeExerciseOrder() {
        return randomizeExerciseOrder;
    }

    public void setRandomizeExerciseOrder(Boolean randomizeExerciseOrder) {
        this.randomizeExerciseOrder = randomizeExerciseOrder;
    }

    public Integer getNumberOfExercisesInExam() {
        return numberOfExercisesInExam;
    }

    public void setNumberOfExercisesInExam(Integer numberOfExercisesInExam) {
        this.numberOfExercisesInExam = numberOfExercisesInExam;
    }

    public Integer getNumberOfCorrectionRoundsInExam() {
        return numberOfCorrectionRoundsInExam;
    }

    public void setNumberOfCorrectionRoundsInExam(Integer numberOfCorrectionRoundsInExam) {
        this.numberOfCorrectionRoundsInExam = numberOfCorrectionRoundsInExam;
    }

    public String getExaminer() {
        return examiner;
    }

    public void setExaminer(String examiner) {
        this.examiner = examiner;
    }

    public String getModuleNumber() {
        return moduleNumber;
    }

    public void setModuleNumber(String moduleNumber) {
        this.moduleNumber = moduleNumber;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public ZonedDateTime getExampleSolutionPublicationDate() {
        return exampleSolutionPublicationDate;
    }

    public void setExampleSolutionPublicationDate(ZonedDateTime exampleSolutionPublicationDate) {
        this.exampleSolutionPublicationDate = exampleSolutionPublicationDate;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public List<ExerciseGroup> getExerciseGroups() {
        return exerciseGroups;
    }

    public void setExerciseGroups(List<ExerciseGroup> exerciseGroups) {
        this.exerciseGroups = exerciseGroups;
    }

    public Set<StudentExam> getStudentExams() {
        return studentExams;
    }

    public void setStudentExams(Set<StudentExam> studentExams) {
        this.studentExams = studentExams;
    }

    public String getExamArchivePath() {
        return examArchivePath;
    }

    public void setExamArchivePath(String examArchivePath) {
        this.examArchivePath = examArchivePath;
    }

    public Set<ExamUser> getExamUsers() {
        return examUsers;
    }

    public void setExamUsers(Set<ExamUser> examUsers) {
        this.examUsers = examUsers;
    }
}
