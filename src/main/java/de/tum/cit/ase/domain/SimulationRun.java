package de.tum.cit.ase.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.Set;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

@Entity
@Table(name = "simulation_run")
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date_time", nullable = false)
    private ZonedDateTime startDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @OneToMany(orphanRemoval = true, mappedBy = "simulationRun")
    private Set<SimulationStats> stats;

    @ManyToOne
    @JoinColumn(name = "simulation_id", nullable = false)
    private Simulation simulation;

    @OneToMany(orphanRemoval = true, mappedBy = "simulationRun")
    private Set<LogMessage> logMessages;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(ZonedDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Set<SimulationStats> getStats() {
        return stats;
    }

    public void setStats(Set<SimulationStats> stats) {
        this.stats = stats;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    public Set<LogMessage> getLogMessages() {
        return logMessages;
    }

    public void setLogMessages(Set<LogMessage> logMessages) {
        this.logMessages = logMessages;
    }

    public enum Status {
        QUEUED,
        RUNNING,
        FINISHED,
        FAILED,
    }
}
