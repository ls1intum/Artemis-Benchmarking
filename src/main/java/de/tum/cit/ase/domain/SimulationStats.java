package de.tum.cit.ase.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.ase.util.TimeLogUtil;
import jakarta.persistence.*;
import java.util.Set;

@Entity
public class SimulationStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "number_of_requests", nullable = false)
    private long numberOfRequests;

    @Column(name = "avg_response_time", nullable = false)
    private long avgResponseTime;

    @OneToMany(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
    @JoinColumn(name = "simulation_stats_id")
    private Set<StatsByMinute> statsByMinute;

    @OneToMany(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
    @JoinColumn(name = "simulation_stats_id")
    private Set<StatsBySecond> statsBySecond;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private RequestType requestType;

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

    public Set<StatsByMinute> getStatsByMinute() {
        return statsByMinute;
    }

    public void setStatsByMinute(Set<StatsByMinute> statsByMinute) {
        this.statsByMinute = statsByMinute;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public SimulationRun getSimulationRun() {
        return simulationRun;
    }

    public void setSimulationRun(SimulationRun simulationRun) {
        this.simulationRun = simulationRun;
    }

    public Set<StatsBySecond> getStatsBySecond() {
        return statsBySecond;
    }

    public void setStatsBySecond(Set<StatsBySecond> statsBySecond) {
        this.statsBySecond = statsBySecond;
    }

    @Override
    public String toString() {
        return (
            "Number of Requests: " + numberOfRequests + "\nAverage Response Time: " + TimeLogUtil.formatDuration(avgResponseTime) + "\n"
        );
    }
}
