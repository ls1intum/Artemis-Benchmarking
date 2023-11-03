package de.tum.cit.ase.domain;

import de.tum.cit.ase.util.TimeLogUtil;
import java.util.Collection;
import java.util.List;

public class SimulationStats {

    private int numberOfRequests;
    private long avgResponseTime;

    public SimulationStats(List<RequestStat> requestStats) {
        numberOfRequests = requestStats.size();
        avgResponseTime = getAverage(requestStats);
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

    private static long getAverage(Collection<RequestStat> times) {
        if (times.isEmpty()) {
            return 0;
        }
        return times.stream().map(RequestStat::duration).reduce(0L, Long::sum) / times.size();
    }

    @Override
    public String toString() {
        return (
            "Number of Requests: " + numberOfRequests + "\nAverage Response Time: " + TimeLogUtil.formatDuration(avgResponseTime) + "\n"
        );
    }
}
