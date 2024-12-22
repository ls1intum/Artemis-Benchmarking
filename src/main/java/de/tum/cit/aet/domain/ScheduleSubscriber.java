package de.tum.cit.aet.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "schedule_subscriber")
public class ScheduleSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "schedule_id", nullable = false)
    @JsonIgnore
    private SimulationSchedule schedule;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "subscription_key", nullable = false, length = 20)
    private String key;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SimulationSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(SimulationSchedule schedule) {
        this.schedule = schedule;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
