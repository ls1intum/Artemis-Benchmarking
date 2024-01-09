package de.tum.cit.ase.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "local_ci_status")
public class LocalCIStatus {

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
    private int timeInMinutes;

    @Column(name = "avg_jobs_per_minute")
    private int avgJobsPerMinute;

    @OneToOne
    @JoinColumn(name = "simulation_run_id", nullable = false)
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

    public int getTimeInMinutes() {
        return timeInMinutes;
    }

    public void setTimeInMinutes(int timeInMinutes) {
        this.timeInMinutes = timeInMinutes;
    }

    public int getAvgJobsPerMinute() {
        return avgJobsPerMinute;
    }

    public void setAvgJobsPerMinute(int avgJobsPerMinute) {
        this.avgJobsPerMinute = avgJobsPerMinute;
    }

    public SimulationRun getSimulationRun() {
        return simulationRun;
    }

    public void setSimulationRun(SimulationRun simulationRun) {
        this.simulationRun = simulationRun;
    }
}
