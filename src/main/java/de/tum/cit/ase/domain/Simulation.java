package de.tum.cit.ase.domain;

import de.tum.cit.ase.util.ArtemisServer;
import jakarta.persistence.*;
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

    @OneToMany(mappedBy = "simulation", fetch = FetchType.EAGER)
    private Set<SimulationRun> runs;

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
}
