package de.tum.cit.ase.service.util;

import java.util.Collection;
import java.util.List;

public class SimulationStats {

    private int numberOfRequests;
    private long avgResponseTime;
    private double failureRate;

    public SimulationStats(List<RequestStat> requestStats) {
        numberOfRequests = requestStats.size();
        avgResponseTime = getAverage(requestStats);
        failureRate = getFailureRate(requestStats);
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

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    private static long getAverage(Collection<RequestStat> times) {
        if (times.isEmpty()) {
            return 0;
        }
        return times.stream().map(RequestStat::duration).reduce(0L, Long::sum) / times.size();
    }

    private static double getFailureRate(Collection<RequestStat> stats) {
        if (stats.isEmpty()) {
            return 0.0;
        }
        long failedRequests = stats.stream().map(RequestStat::success).filter(s -> !s).count();
        return (double) failedRequests / stats.size();
    }

    @Override
    public String toString() {
        return (
            "Number of Requests: " +
            numberOfRequests +
            "\nAverage Response Time: " +
            TimeLogUtil.formatDuration(avgResponseTime) +
            "\nFailure Rate: " +
            failureRate +
            "\n"
        );
    }
}
