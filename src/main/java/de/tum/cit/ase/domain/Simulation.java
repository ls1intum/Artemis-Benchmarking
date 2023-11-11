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
}
