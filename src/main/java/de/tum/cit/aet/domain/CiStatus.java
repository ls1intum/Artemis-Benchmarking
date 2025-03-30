package de.tum.cit.aet.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "ci_status")
public class CiStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_finished")
    private boolean isFinished;

    @Column(name = "queued_jobs")
    private int queuedJobs;

    @Column(name = "total_jobs")
    private int totalJobs;

    @Column(name = "time_in_minutes")
    private long timeInMinutes;

    @JsonIgnore
    @Column(name = "start_time_nanos")
    private long startTimeNanos;

    @Column(name = "avg_jobs_per_minute")
    private double avgJobsPerMinute;

    @OneToOne
    @JoinColumn(name = "simulation_run_id", nullable = false)
    @JsonIgnore
    private SimulationRun simulationRun;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void setFinished(boolean finished) {
        isFinished = finished;
    }

    public int getQueuedJobs() {
        return queuedJobs;
    }

    public void setQueuedJobs(int queuedJobs) {
        this.queuedJobs = queuedJobs;
    }

    public int getTotalJobs() {
        return totalJobs;
    }

    public void setTotalJobs(int totalJobs) {
        this.totalJobs = totalJobs;
    }

    public long getTimeInMinutes() {
        return timeInMinutes;
    }

    public void setTimeInMinutes(long timeInMinutes) {
        this.timeInMinutes = timeInMinutes;
    }

    public double getAvgJobsPerMinute() {
        return avgJobsPerMinute;
    }

    public void setAvgJobsPerMinute(double avgJobsPerMinute) {
        this.avgJobsPerMinute = avgJobsPerMinute;
    }

    public SimulationRun getSimulationRun() {
        return simulationRun;
    }

    public void setSimulationRun(SimulationRun simulationRun) {
        this.simulationRun = simulationRun;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public void setStartTimeNanos(long startTimeNanos) {
        this.startTimeNanos = startTimeNanos;
    }
}
