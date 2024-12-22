package de.tum.cit.aet.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.aet.util.ArtemisAccountDTO;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.Set;

@Entity
@Table(name = "simulation_run")
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date_time", nullable = false)
    private ZonedDateTime startDateTime;

    @Column(name = "end_date_time")
    private ZonedDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @OneToMany(cascade = CascadeType.REMOVE, mappedBy = "simulationRun")
    private Set<SimulationStats> stats;

    @ManyToOne
    @JoinColumn(name = "simulation_id", nullable = false)
    @JsonIgnore
    private Simulation simulation;

    @OneToMany(cascade = CascadeType.REMOVE, mappedBy = "simulationRun")
    private Set<LogMessage> logMessages;

    @OneToOne(cascade = CascadeType.REMOVE, mappedBy = "simulationRun", fetch = FetchType.EAGER)
    private CiStatus ciStatus;

    @Transient
    private ArtemisAccountDTO adminAccount;

    @Transient
    private SimulationSchedule schedule;

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

    public ArtemisAccountDTO getAdminAccount() {
        return adminAccount;
    }

    public void setAdminAccount(ArtemisAccountDTO adminAccount) {
        this.adminAccount = adminAccount;
    }

    public SimulationSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(SimulationSchedule schedule) {
        this.schedule = schedule;
    }

    public ZonedDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(ZonedDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public CiStatus getCiStatus() {
        return ciStatus;
    }

    public void setCiStatus(CiStatus ciStatus) {
        this.ciStatus = ciStatus;
    }

    public enum Status {
        QUEUED,
        RUNNING,
        FINISHED,
        FAILED,
        CANCELLED,
    }
}
