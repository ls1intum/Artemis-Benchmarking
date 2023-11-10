package de.tum.cit.ase.domain;

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
    private int numberOfRequests;

    @Column(name = "avg_response_time", nullable = false)
    private long avgResponseTime;

    @Column(name = "simulation_stats_id", nullable = false)
    private Long simulationStatsId;

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

    public int getNumberOfRequests() {
        return numberOfRequests;
    }

    public void setNumberOfRequests(int numberOfRequests) {
        this.numberOfRequests = numberOfRequests;
    }

    public long getAvgResponseTime() {
        return avgResponseTime;
    }

    public void setAvgResponseTime(long avgResponseTime) {
        this.avgResponseTime = avgResponseTime;
    }

    public Long getSimulationStatsId() {
        return simulationStatsId;
    }

    public void setSimulationStatsId(Long simulationStatsId) {
        this.simulationStatsId = simulationStatsId;
    }
}
