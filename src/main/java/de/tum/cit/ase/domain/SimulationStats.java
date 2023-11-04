package de.tum.cit.ase.domain;

import de.tum.cit.ase.util.TimeLogUtil;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimulationStats {

    private int numberOfRequests;
    private long avgResponseTime;
    private Map<ZonedDateTime, Long> requestsByMinute;
    private Map<ZonedDateTime, Double> avgResponseTimeByMinute;

    public SimulationStats(List<RequestStat> requestStats) {
        numberOfRequests = requestStats.size();
        avgResponseTime = getAverage(requestStats);
        requestsByMinute = calculateRequestsByMinute(requestStats);
        avgResponseTimeByMinute = calculateAvgResponseTimeByMinute(requestStats);
    }

    public SimulationStats() {}

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

    public Map<ZonedDateTime, Long> getRequestsByMinute() {
        return requestsByMinute;
    }

    public void setRequestsByMinute(Map<ZonedDateTime, Long> requestsByMinute) {
        this.requestsByMinute = requestsByMinute;
    }

    public Map<ZonedDateTime, Double> getAvgResponseTimeByMinute() {
        return avgResponseTimeByMinute;
    }

    public void setAvgResponseTimeByMinute(Map<ZonedDateTime, Double> avgResponseTimeByMinute) {
        this.avgResponseTimeByMinute = avgResponseTimeByMinute;
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
