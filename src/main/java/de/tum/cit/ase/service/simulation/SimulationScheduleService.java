package de.tum.cit.ase.service.simulation;

import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.domain.ScheduleSubscriber;
import de.tum.cit.ase.domain.SimulationSchedule;
import de.tum.cit.ase.repository.ScheduleSubscriberRepository;
import de.tum.cit.ase.repository.SimulationScheduleRepository;
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

    public SimulationScheduleService(
        SimulationScheduleRepository simulationScheduleRepository,
        SimulationDataService simulationDataService,
        ScheduleSubscriberRepository scheduleSubscriberRepository
    ) {
        this.simulationScheduleRepository = simulationScheduleRepository;
        this.simulationDataService = simulationDataService;
        this.scheduleSubscriberRepository = scheduleSubscriberRepository;
    }

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

    public void deleteSimulationSchedule(long simulationScheduleId) {
        log.debug("Deleting simulation schedule {}", simulationScheduleId);
        simulationScheduleRepository.deleteById(simulationScheduleId);
    }

    public List<SimulationSchedule> getSimulationSchedules(long simulationId) {
        return simulationScheduleRepository.findAllBySimulationId(simulationId);
    }

    public ScheduleSubscriber subscribeToSchedule(long scheduleId, String email) {
        var schedule = simulationScheduleRepository.findById(scheduleId).orElseThrow();
        if (schedule.getSubscribers().stream().anyMatch(subscriber -> subscriber.getEmail().equals(email))) {
            throw new IllegalArgumentException("Already subscribed to this schedule");
        }
        var subscriber = new ScheduleSubscriber();
        subscriber.setSchedule(schedule);
        subscriber.setEmail(email);
        subscriber.setKey(RandomUtil.generateActivationKey());
        return scheduleSubscriberRepository.save(subscriber);
    }

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
                simulationDataService.createAndQueueSimulationRun(simulation.getId(), null);
                updateNextRun(simulationSchedule);
            });
    }

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

    private ZonedDateTime calculateNextRun(SimulationSchedule simulationSchedule) {
        ZonedDateTime lookFrom;
        if (simulationSchedule.getStartDateTime().isAfter(now(ZoneId.of("UTC")))) {
            lookFrom = simulationSchedule.getStartDateTime();
        } else {
            lookFrom = now(ZoneId.of("UTC"));
        }
        ZonedDateTime time = simulationSchedule
            .getTimeOfDay()
            .withYear(lookFrom.getYear())
            .withMonth(lookFrom.getMonthValue())
            .withDayOfMonth(lookFrom.getDayOfMonth());

        if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.DAILY) {
            if (time.isBefore(lookFrom)) {
                return time.plusDays(1);
            } else {
                return time;
            }
        } else {
            if (lookFrom.getDayOfWeek() == simulationSchedule.getDayOfWeek() && !time.isBefore(lookFrom)) {
                return time;
            }
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
