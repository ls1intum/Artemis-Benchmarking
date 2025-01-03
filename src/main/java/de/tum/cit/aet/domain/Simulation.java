package de.tum.cit.aet.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.aet.util.ArtemisServer;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.Set;

@Entity
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "number_of_users", nullable = false)
    private int numberOfUsers;

    @Column(name = "exam_id", nullable = false)
    private long examId;

    @Column(name = "course_id", nullable = false)
    private long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtemisServer server;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @OneToMany(mappedBy = "simulation", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    private Set<SimulationRun> runs;

    @Column(name = "creation_date", nullable = false)
    private ZonedDateTime creationDate;

    @Column(name = "customize_user_range")
    private boolean customizeUserRange = false;

    @Column(name = "user_range")
    private String userRange;

    @Deprecated
    @JsonIgnore
    @Enumerated(EnumType.STRING)
    @Column(name = "ide_type", nullable = false)
    private IDEType ideType;

    @Column(name = "onlineide_percentage", nullable = false)
    private double onlineIdePercentage;

    @Column(name = "password_percentage", nullable = false)
    private double passwordPercentage;

    @Column(name = "token_percentage", nullable = false)
    private double tokenPercentage;

    @Column(name = "ssh_percentage", nullable = false)
    private double sshPercentage;

    @Column(name = "number_of_commits_and_pushes_from")
    private int numberOfCommitsAndPushesFrom;

    @Column(name = "number_of_commits_and_pushes_to")
    private int numberOfCommitsAndPushesTo;

    @OneToMany(mappedBy = "simulation", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    @JsonIgnore
    private Set<SimulationSchedule> schedules;

    // Instructor credentials, only used for EXISTING_COURSE_UNPREPARED_EXAM or EXISTING_COURSE_CREATE_EXAM on PRODUCTION
    // The credentials are optional, but required for scheduled simulations
    // They must NEVER be sent to the client!
    @Column(name = "instructor_username")
    private String instructorUsername;

    @Column(name = "instructor_password")
    private String instructorPassword;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumberOfUsers() {
        return numberOfUsers;
    }

    public void setNumberOfUsers(int numberOfUsers) {
        this.numberOfUsers = numberOfUsers;
    }

    public long getExamId() {
        return examId;
    }

    public void setExamId(long examId) {
        this.examId = examId;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public ArtemisServer getServer() {
        return server;
    }

    public void setServer(ArtemisServer server) {
        this.server = server;
    }

    public Set<SimulationRun> getRuns() {
        return runs;
    }

    public void setRuns(Set<SimulationRun> runs) {
        this.runs = runs;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public boolean isCustomizeUserRange() {
        return customizeUserRange;
    }

    public void setCustomizeUserRange(boolean customizeUserRange) {
        this.customizeUserRange = customizeUserRange;
    }

    public String getUserRange() {
        return userRange;
    }

    public void setUserRange(String userRange) {
        this.userRange = userRange;
    }

    public IDEType getIdeType() {
        return ideType;
    }

    public void setIdeType(IDEType ideType) {
        this.ideType = ideType;
    }

    public int getNumberOfCommitsAndPushesFrom() {
        return numberOfCommitsAndPushesFrom;
    }

    public void setNumberOfCommitsAndPushesFrom(int numberOfCommitsAndPushesFrom) {
        this.numberOfCommitsAndPushesFrom = numberOfCommitsAndPushesFrom;
    }

    public int getNumberOfCommitsAndPushesTo() {
        return numberOfCommitsAndPushesTo;
    }

    public void setNumberOfCommitsAndPushesTo(int numberOfCommitsAndPushesTo) {
        this.numberOfCommitsAndPushesTo = numberOfCommitsAndPushesTo;
    }

    public Set<SimulationSchedule> getSchedules() {
        return schedules;
    }

    public void setSchedules(Set<SimulationSchedule> schedules) {
        this.schedules = schedules;
    }

    public String getInstructorUsername() {
        return instructorUsername;
    }

    public void setInstructorUsername(String instructorUsername) {
        this.instructorUsername = instructorUsername;
    }

    public String getInstructorPassword() {
        return instructorPassword;
    }

    public void setInstructorPassword(String instructorPassword) {
        this.instructorPassword = instructorPassword;
    }

    /**
     * @return true if instructor credentials are provided, false otherwise
     */
    public boolean instructorCredentialsProvided() {
        if (mode == Mode.CREATE_COURSE_AND_EXAM) {
            // For this mode we need admin credentials, not instructor credentials
            return false;
        }
        return instructorUsername != null && instructorPassword != null;
    }

    public double getOnlineIdePercentage() {
        return onlineIdePercentage;
    }

    public void setOnlineIdePercentage(double onlineIdePercentage) {
        this.onlineIdePercentage = onlineIdePercentage;
    }

    public double getPasswordPercentage() {
        return passwordPercentage;
    }

    public void setPasswordPercentage(double passwordPercentage) {
        this.passwordPercentage = passwordPercentage;
    }

    public double getTokenPercentage() {
        return tokenPercentage;
    }

    public void setTokenPercentage(double tokenPercentage) {
        this.tokenPercentage = tokenPercentage;
    }

    public double getSshPercentage() {
        return sshPercentage;
    }

    public void setSshPercentage(double sshPercentage) {
        this.sshPercentage = sshPercentage;
    }

    public boolean participationPercentagesSumUpToHundredPercent() {
        return (this.onlineIdePercentage + this.passwordPercentage + this.tokenPercentage + this.sshPercentage) == 100.0;
    }

    public enum Mode {
        /**
         * We create a temporary course and exam, prepare the exam and delete everything afterwards.
         * Required rights: ADMIN
         */
        CREATE_COURSE_AND_EXAM,

        /**
         * We use an existing course and exam and prepare the exam (= generate student-exams, prepare exercise start, change start time).
         * Required rights: INSTRUCTOR
         */
        EXISTING_COURSE_UNPREPARED_EXAM,

        /**
         * We use an existing course and exam that is already fully prepared.
         * Required rights: NONE
         */
        EXISTING_COURSE_PREPARED_EXAM,

        /**
         * We use an existing course and create a temporary exam, prepare the exam and delete the exam afterwards.
         * Required rights: INSTRUCTOR
         */
        EXISTING_COURSE_CREATE_EXAM,
    }

    public enum IDEType {
        /**
         * Programming exercises will be solved using the Artemis Online IDE.
         */
        ONLINE,

        /**
         * Programming exercises will be solved using an offline IDE.
         */
        OFFLINE,
    }
}
