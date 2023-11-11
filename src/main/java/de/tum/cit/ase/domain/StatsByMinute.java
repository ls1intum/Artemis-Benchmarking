package de.tum.cit.ase.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
public class StatsByMinute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_time", nullable = false)
    private ZonedDateTime dateTime;

    @Column(name = "number_of_requests", nullable = false)
    private long numberOfRequests;

    @Column(name = "avg_response_time", nullable = false)
    private long avgResponseTime;

    @ManyToOne
    @JoinColumn(name = "simulation_stats_id", nullable = false)
    @JsonIgnore
    private SimulationStats simulationStats;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(ZonedDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public long getNumberOfRequests() {
        return numberOfRequests;
    }

    public void setNumberOfRequests(long numberOfRequests) {
        this.numberOfRequests = numberOfRequests;
    }

    public long getAvgResponseTime() {
        return avgResponseTime;
    }

    public void setAvgResponseTime(long avgResponseTime) {
        this.avgResponseTime = avgResponseTime;
    }

    public SimulationStats getSimulationStats() {
        return simulationStats;
    }

    public void setSimulationStats(SimulationStats simulationStats) {
        this.simulationStats = simulationStats;
    }
}
