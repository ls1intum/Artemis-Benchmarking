package de.tum.cit.aet.service.simulation;

import static java.time.ZonedDateTime.now;

import de.tum.cit.aet.domain.ScheduleSubscriber;
import de.tum.cit.aet.domain.SimulationSchedule;
import de.tum.cit.aet.repository.ScheduleSubscriberRepository;
import de.tum.cit.aet.repository.SimulationScheduleRepository;
import de.tum.cit.aet.service.MailService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.jhipster.security.RandomUtil;

@Service
public class SimulationScheduleService {

    private final Logger log = LoggerFactory.getLogger(SimulationScheduleService.class);

    private final SimulationScheduleRepository simulationScheduleRepository;
    private final SimulationDataService simulationDataService;
    private final ScheduleSubscriberRepository scheduleSubscriberRepository;
    private final MailService mailService;

    public SimulationScheduleService(
        SimulationScheduleRepository simulationScheduleRepository,
        SimulationDataService simulationDataService,
        ScheduleSubscriberRepository scheduleSubscriberRepository,
        MailService mailService
    ) {
        this.simulationScheduleRepository = simulationScheduleRepository;
        this.simulationDataService = simulationDataService;
        this.scheduleSubscriberRepository = scheduleSubscriberRepository;
        this.mailService = mailService;
    }

    /**
     * Create a new simulation schedule for a simulation
     *
     * @param simulationId the id of the simulation
     * @param simulationSchedule the schedule to create
     * @return the created schedule
     * @throws IllegalArgumentException if the schedule is invalid
     */
    public SimulationSchedule createSimulationSchedule(long simulationId, SimulationSchedule simulationSchedule) {
        log.debug("Creating simulation schedule for simulation {}", simulationId);
        if (simulationSchedule == null) {
            throw new IllegalArgumentException("Simulation schedule must not be null");
        } else if (simulationSchedule.getId() != null) {
            throw new IllegalArgumentException("Simulation schedule must not have an id yet");
        } else if (simulationSchedule.getSimulation() != null) {
            throw new IllegalArgumentException("Simulation schedule must not have a simulation yet");
        }
        verifySchedule(simulationSchedule);
        simulationSchedule.setSimulation(simulationDataService.getSimulation(simulationId));
        return updateNextRun(simulationSchedule);
    }

    /**
     * Update an existing simulation schedule
     *
     * @param simulationScheduleId the id of the schedule to update
     * @param simulationSchedule the updated schedule
     * @return the updated schedule
     * @throws IllegalArgumentException if the schedule is invalid
     */
    public SimulationSchedule updateSimulationSchedule(long simulationScheduleId, SimulationSchedule simulationSchedule) {
        log.debug("Updating simulation schedule {}", simulationScheduleId);
        if (simulationSchedule == null) {
            throw new IllegalArgumentException("Simulation schedule must not be null");
        } else if (simulationScheduleId != simulationSchedule.getId()) {
            throw new IllegalArgumentException("Invalid id!");
        }
        var existingSimulationSchedule = simulationScheduleRepository.findById(simulationScheduleId).orElseThrow();
        if (simulationSchedule.getSimulation() != null) {
            throw new IllegalArgumentException("Id of simulation must not be changed!");
        }
        verifySchedule(simulationSchedule);
        simulationSchedule.setSimulation(existingSimulationSchedule.getSimulation());
        return updateNextRun(simulationSchedule);
    }

    /**
     * Delete a simulation schedule
     *
     * @param simulationScheduleId the id of the schedule to delete
     */
    public void deleteSimulationSchedule(long simulationScheduleId) {
        log.debug("Deleting simulation schedule {}", simulationScheduleId);
        simulationScheduleRepository.deleteById(simulationScheduleId);
    }

    /**
     * Get all schedules for a simulation
     *
     * @param simulationId the id of the simulation
     * @return the schedules
     */
    public List<SimulationSchedule> getSimulationSchedules(long simulationId) {
        return simulationScheduleRepository.findAllBySimulationId(simulationId);
    }

    /**
     * Subscribe to a schedule
     *
     * @param scheduleId the id of the schedule to subscribe to
     * @param email the email of the subscriber
     */
    public ScheduleSubscriber subscribeToSchedule(long scheduleId, String email) {
        log.debug("Subscribing {} to schedule {}", email, scheduleId);
        var schedule = simulationScheduleRepository.findById(scheduleId).orElseThrow();
        if (schedule.getSubscribers().stream().anyMatch(subscriber -> subscriber.getEmail().equals(email))) {
            log.debug("Subscriber {} already subscribed to schedule {}", email, scheduleId);
            return null;
        }
        var subscriber = new ScheduleSubscriber();
        subscriber.setSchedule(schedule);
        subscriber.setEmail(email.toLowerCase());
        subscriber.setKey(RandomUtil.generateActivationKey());
        var savedSubscriber = scheduleSubscriberRepository.save(subscriber);
        mailService.sendSubscribedMail(savedSubscriber);
        return savedSubscriber;
    }

    /**
     * Unsubscribe from a schedule
     *
     * @param key the key of the subscription
     */
    public void unsubscribeFromSchedule(String key) {
        log.debug("Unsubscribing from schedule with key {}", key);
        var subscriber = scheduleSubscriberRepository.findByKey(key).orElseThrow();
        scheduleSubscriberRepository.delete(subscriber);
    }

    /**
     * Automatically called every minute.
     * <p>
     * Executes all scheduled simulations that are due.
     */
    @Scheduled(fixedRate = 1000 * 60, initialDelay = 0)
    void executeScheduledSimulations() {
        log.info("Executing scheduled simulation runs");
        var simulationSchedules = simulationScheduleRepository.findAll();
        simulationSchedules
            .stream()
            .filter(simulationSchedule -> simulationSchedule.getNextRun().isBefore(now()))
            .forEach(simulationSchedule -> {
                log.info("Executing scheduled simulation run for simulation {}", simulationSchedule.getSimulation().getId());
                var simulation = simulationSchedule.getSimulation();
                simulationDataService.createAndQueueSimulationRun(simulation.getId(), null, simulationSchedule);
                updateNextRun(simulationSchedule);
            });
    }

    /**
     * Update a schedule by calculating the time of its next run.
     * Deletes the schedule if it has ended.
     *
     * @param simulationSchedule the schedule to update
     * @return the updated schedule or null if the schedule has ended
     */
    private SimulationSchedule updateNextRun(SimulationSchedule simulationSchedule) {
        var nextRun = calculateNextRun(simulationSchedule);
        if (simulationSchedule.getEndDateTime() != null && nextRun.isAfter(simulationSchedule.getEndDateTime())) {
            simulationScheduleRepository.delete(simulationSchedule);
            return null;
        } else {
            simulationSchedule.setNextRun(nextRun);
            return simulationScheduleRepository.save(simulationSchedule);
        }
    }

    /**
     * Calculate the time of the next run of a schedule.
     *
     * @param simulationSchedule the schedule
     * @return the time of the next run
     */
    private ZonedDateTime calculateNextRun(SimulationSchedule simulationSchedule) {
        // If the start date is in the future, we start looking from there
        // Otherwise, we start looking from now
        ZonedDateTime lookFrom;
        if (simulationSchedule.getStartDateTime().isAfter(now(ZoneId.of("UTC")))) {
            lookFrom = simulationSchedule.getStartDateTime();
        } else {
            lookFrom = now(ZoneId.of("UTC"));
        }

        // Set the time to the time of day of the schedule
        // Set the date to the lookFrom date
        // This is the earliest possible time for the next run
        ZonedDateTime time = simulationSchedule
            .getTimeOfDay()
            .withYear(lookFrom.getYear())
            .withMonth(lookFrom.getMonthValue())
            .withDayOfMonth(lookFrom.getDayOfMonth());

        if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.DAILY) {
            // If the time is before the lookFrom time we have to add a day
            // This means that the timeOfDay of the schedule is already over for "today"
            if (time.isBefore(lookFrom)) {
                return time.plusDays(1);
            } else {
                return time;
            }
        } else {
            // If the weekday of lookFrom is correct and the time is not over yet, we have found the next run
            if (lookFrom.getDayOfWeek() == simulationSchedule.getDayOfWeek() && !time.isBefore(lookFrom)) {
                return time;
            }
            // Otherwise we have to look for the next matching weekday
            return time.with(TemporalAdjusters.next(simulationSchedule.getDayOfWeek()));
        }
    }

    private void verifySchedule(SimulationSchedule simulationSchedule) {
        if (simulationSchedule.getStartDateTime() == null) {
            throw new IllegalArgumentException("Start date time must not be null");
        } else if (
            simulationSchedule.getEndDateTime() != null &&
            simulationSchedule.getEndDateTime().isBefore(simulationSchedule.getStartDateTime())
        ) {
            throw new IllegalArgumentException("End date time must not be before start date time");
        } else if (simulationSchedule.getCycle() == null) {
            throw new IllegalArgumentException("Cycle must not be null");
        } else if (simulationSchedule.getEndDateTime() != null && simulationSchedule.getEndDateTime().isBefore(now())) {
            throw new IllegalArgumentException("End date time must not be in the past");
        } else if (simulationSchedule.getTimeOfDay() == null) {
            throw new IllegalArgumentException("Time of day must not be null");
        } else if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.WEEKLY && simulationSchedule.getDayOfWeek() == null) {
            throw new IllegalArgumentException("Day of week must not be null");
        }
    }
}
