package de.tum.cit.ase.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.io.Serial;
import java.time.ZonedDateTime;

@Entity
public class LogMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;
    private boolean isError;

    private ZonedDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "simulation_run_id", nullable = false)
    @JsonIgnore
    private SimulationRun simulationRun;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public SimulationRun getSimulationRun() {
        return simulationRun;
    }

    public void setSimulationRun(SimulationRun simulationRun) {
        this.simulationRun = simulationRun;
    }
}
