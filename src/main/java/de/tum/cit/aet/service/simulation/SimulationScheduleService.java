package de.tum.cit.aet.service.simulation;

import de.tum.cit.aet.domain.ScheduleSubscriber;
import de.tum.cit.aet.domain.SimulationSchedule;
import de.tum.cit.aet.repository.ScheduleSubscriberRepository;
import de.tum.cit.aet.repository.SimulationScheduleRepository;
import de.tum.cit.aet.service.MailService;
import de.tum.cit.aet.util.RandomUtil;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SimulationScheduleService {

    private final Logger log = LoggerFactory.getLogger(SimulationScheduleService.class);

    private final SimulationScheduleRepository simulationScheduleRepository;
    private final SimulationDataService simulationDataService;
    private final ScheduleSubscriberRepository scheduleSubscriberRepository;
    private final MailService mailService;

    /**
     * The clock every scheduling decision is made against.
     * <p>
     * Injected rather than read from {@code ZonedDateTime.now()} so the behaviour can be tested at a chosen instant.
     * Without it the tests had to express "later today" as an offset from the real clock, which stops being later today
     * once the offset crosses midnight: four of them failed every night between 23:00 and 01:00 UTC.
     */
    private final Clock clock;

    public SimulationScheduleService(
        SimulationScheduleRepository simulationScheduleRepository,
        SimulationDataService simulationDataService,
        ScheduleSubscriberRepository scheduleSubscriberRepository,
        MailService mailService,
        Clock clock
    ) {
        this.simulationScheduleRepository = simulationScheduleRepository;
        this.simulationDataService = simulationDataService;
        this.scheduleSubscriberRepository = scheduleSubscriberRepository;
        this.mailService = mailService;
        this.clock = clock;
    }

    /**
     * Create a new simulation schedule for a simulation
     *
     * @param simulationId the id of the simulation
     * @param simulationSchedule the schedule to create
     * @return the created schedule
     */
    public SimulationSchedule createSimulationSchedule(long simulationId, SimulationSchedule simulationSchedule) {
        log.debug("Creating simulation schedule for simulation {}", simulationId);
        if (simulationSchedule == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation schedule must not be null");
        } else if (simulationSchedule.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation schedule must not have an id yet");
        } else if (simulationSchedule.getSimulation() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation schedule must not have a simulation yet");
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
     */
    public SimulationSchedule updateSimulationSchedule(long simulationScheduleId, SimulationSchedule simulationSchedule) {
        log.debug("Updating simulation schedule {}", simulationScheduleId);
        if (simulationSchedule == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation schedule must not be null");
        } else if (simulationScheduleId != simulationSchedule.getId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id in simulation schedule!");
        }
        var existingSimulationSchedule = simulationScheduleRepository.findById(simulationScheduleId).orElseThrow();
        if (simulationSchedule.getSimulation() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id of simulation must not be changed!");
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
     * @param email      the email of the subscriber
     */
    public void subscribeToSchedule(long scheduleId, String email) {
        log.debug("Subscribing {} to schedule {}", email, scheduleId);
        var schedule = simulationScheduleRepository.findById(scheduleId).orElseThrow();
        if (
            schedule
                .getSubscribers()
                .stream()
                .anyMatch(subscriber -> subscriber.getEmail().equals(email))
        ) {
            log.debug("Subscriber {} already subscribed to schedule {}", email, scheduleId);
            return;
        }
        var subscriber = new ScheduleSubscriber();
        subscriber.setSchedule(schedule);
        subscriber.setEmail(email.toLowerCase());
        subscriber.setKey(RandomUtil.generateActivationKey());
        var savedSubscriber = scheduleSubscriberRepository.save(subscriber);
        mailService.sendSubscribedMail(savedSubscriber);
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
        final var simulationSchedules = simulationScheduleRepository.findAll();
        log.info("Executing {} scheduled simulation runs", simulationSchedules.size());
        simulationSchedules
            .stream()
            .filter(simulationSchedule -> simulationSchedule.getNextRun().isBefore(ZonedDateTime.now(clock)))
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
    /**
     * The current time in UTC, which is the zone every schedule is reasoned about in.
     *
     * @return now, according to the injected clock
     */
    private ZonedDateTime nowUtc() {
        return ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of("UTC"));
    }

    private ZonedDateTime calculateNextRun(SimulationSchedule simulationSchedule) {
        // If the start date is in the future, we start looking from there
        // Otherwise, we start looking from now
        ZonedDateTime lookFrom;
        if (simulationSchedule.getStartDateTime().isAfter(nowUtc())) {
            lookFrom = simulationSchedule.getStartDateTime();
        } else {
            lookFrom = nowUtc();
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date time must not be null in simulation schedule");
        } else if (
            simulationSchedule.getEndDateTime() != null &&
            simulationSchedule.getEndDateTime().isBefore(simulationSchedule.getStartDateTime())
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date time must not be before start date time");
        } else if (simulationSchedule.getCycle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cycle must not be null in simulation schedule");
        } else if (simulationSchedule.getEndDateTime() != null && simulationSchedule.getEndDateTime().isBefore(ZonedDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date time must not be in the past in simulation schedule");
        } else if (simulationSchedule.getTimeOfDay() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time of day must not be null in simulation schedule");
        } else if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.WEEKLY && simulationSchedule.getDayOfWeek() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Day of week must not be null in simulation schedule");
        }
    }
}
