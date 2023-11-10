package de.tum.cit.ase.domain;

import de.tum.cit.ase.util.TimeLogUtil;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class SimulationStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "number_of_requests", nullable = false)
    private int numberOfRequests;

    @Column(name = "avg_response_time", nullable = false)
    private long avgResponseTime;

    @OneToMany(orphanRemoval = true)
    @JoinColumn(name = "simulation_stats_id")
    private Set<StatsByMinute> statsByMinute;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private RequestType requestType;

    @ManyToOne
    @JoinColumn(name = "simulation_run_id", nullable = false)
    private SimulationRun simulationRun;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    private static long getAverage(Collection<RequestStat> times) {
        if (times.isEmpty()) {
            return 0;
        }
        return times.stream().map(RequestStat::duration).reduce(0L, Long::sum) / times.size();
    }

    private static Map<ZonedDateTime, Long> calculateRequestsByMinute(Collection<RequestStat> requestStats) {
        return requestStats
            .stream()
            .collect(Collectors.groupingBy(stat -> stat.dateTime().truncatedTo(ChronoUnit.MINUTES), Collectors.counting()));
    }

    private static Map<ZonedDateTime, Double> calculateAvgResponseTimeByMinute(Collection<RequestStat> requestStats) {
        return requestStats
            .stream()
            .collect(
                Collectors.groupingBy(
                    stat -> stat.dateTime().truncatedTo(ChronoUnit.MINUTES),
                    Collectors.averagingLong(RequestStat::duration)
                )
            );
    }

    @Override
    public String toString() {
        return (
            "Number of Requests: " + numberOfRequests + "\nAverage Response Time: " + TimeLogUtil.formatDuration(avgResponseTime) + "\n"
        );
    }
}
