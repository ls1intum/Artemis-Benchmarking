package de.tum.cit.ase.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;

@Entity
@Table(name = "simulation_schedule")
public class SimulationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date_time", nullable = false)
    private ZonedDateTime startDateTime;

    @Column(name = "end_date_time")
    private ZonedDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cycle cycle;

    @ManyToOne
    @JoinColumn(name = "simulation_id", nullable = false)
    @JsonIgnore
    private Simulation simulation;

    @Column(name = "next_run", nullable = false)
    @JsonIgnore
    private ZonedDateTime nextRun;

    @Column(name = "time_of_day", nullable = false)
    private ZonedDateTime timeOfDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(ZonedDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public ZonedDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(ZonedDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public Cycle getCycle() {
        return cycle;
    }

    public void setCycle(Cycle cycle) {
        this.cycle = cycle;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    public ZonedDateTime getNextRun() {
        return nextRun;
    }

    public void setNextRun(ZonedDateTime nextRun) {
        this.nextRun = nextRun;
    }

    public ZonedDateTime getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(ZonedDateTime timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public enum Cycle {
        DAILY,
        WEEKLY,
    }
}
