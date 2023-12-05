package de.tum.cit.ase.service.simulation;

import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.domain.SimulationSchedule;
import de.tum.cit.ase.repository.SimulationScheduleRepository;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SimulationScheduleService {

    private final Logger log = LoggerFactory.getLogger(SimulationScheduleService.class);

    private final SimulationScheduleRepository simulationScheduleRepository;
    private final SimulationDataService simulationDataService;

    public SimulationScheduleService(
        SimulationScheduleRepository simulationScheduleRepository,
        SimulationDataService simulationDataService
    ) {
        this.simulationScheduleRepository = simulationScheduleRepository;
        this.simulationDataService = simulationDataService;
    }

    public SimulationSchedule createSimulationSchedule(long simulationId, SimulationSchedule simulationSchedule) {
        log.debug("Creating simulation schedule for simulation {}", simulationId);
        if (simulationSchedule == null) {
            throw new IllegalArgumentException("Simulation schedule must not be null");
        } else if (simulationSchedule.getId() != null) {
            throw new IllegalArgumentException("Simulation schedule must not have an id yet");
        } else if (simulationSchedule.getSimulation() != null) {
            throw new IllegalArgumentException("Simulation schedule must not have a simulation yet");
        } else if (simulationSchedule.getStartDateTime() == null) {
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
        } else if (!verifySchedule(simulationSchedule)) {
            throw new IllegalArgumentException("Invalid schedule");
        }
        simulationSchedule.setNextRun(calculateNextRun(simulationSchedule));
        simulationSchedule.setSimulation(simulationDataService.getSimulation(simulationId));
        return simulationScheduleRepository.save(simulationSchedule);
    }

    public SimulationSchedule updateSimulationSchedule(long simulationScheduleId, SimulationSchedule simulationSchedule) {
        log.debug("Updating simulation schedule {}", simulationScheduleId);
        if (simulationSchedule == null) {
            throw new IllegalArgumentException("Simulation schedule must not be null");
        } else if (simulationScheduleId != simulationSchedule.getId()) {
            throw new IllegalArgumentException("Invalid id!");
        }
        var existingSimulationSchedule = simulationScheduleRepository.findById(simulationScheduleId).orElseThrow();
        if (!Objects.equals(existingSimulationSchedule.getSimulation().getId(), simulationSchedule.getSimulation().getId())) {
            throw new IllegalArgumentException("Id of simulation must not be changed!");
        } else if (simulationSchedule.getStartDateTime() == null) {
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
        } else if (!verifySchedule(simulationSchedule)) {
            throw new IllegalArgumentException("Invalid schedule");
        }
        simulationSchedule.setNextRun(calculateNextRun(simulationSchedule));
        return simulationScheduleRepository.save(simulationSchedule);
    }

    public void deleteSimulationSchedule(long simulationScheduleId) {
        log.debug("Deleting simulation schedule {}", simulationScheduleId);
        simulationScheduleRepository.deleteById(simulationScheduleId);
    }

    public List<SimulationSchedule> getSimulationSchedules(long simulationId) {
        return simulationScheduleRepository.findAllBySimulationId(simulationId);
    }

    @Scheduled(fixedRate = 1000 * 60 * 15, initialDelay = 0)
    void executeScheduledSimulation() {
        log.info("Executing scheduled simulation runs");
        var simulationSchedules = simulationScheduleRepository.findAll();
        simulationSchedules
            .stream()
            .filter(simulationSchedule -> simulationSchedule.getNextRun().isBefore(now()))
            .forEach(simulationSchedule -> {
                log.info("Executing scheduled simulation run for simulation {}", simulationSchedule.getSimulation().getId());
                var simulation = simulationSchedule.getSimulation();
                simulationDataService.createAndQueueSimulationRun(simulation.getId(), null);
                simulationSchedule.setNextRun(calculateNextRun(simulationSchedule));
                simulationScheduleRepository.save(simulationSchedule);
            });
    }

    private ZonedDateTime calculateNextRun(SimulationSchedule simulationSchedule) {
        LocalTime time = simulationSchedule.getTimeOfDay().toLocalTime();
        if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.DAILY) {
            if (time.isBefore(LocalTime.now())) {
                return now().plusDays(1).withHour(time.getHour()).withMinute(time.getMinute());
            } else {
                return now().withHour(time.getHour()).withMinute(time.getMinute());
            }
        } else {
            if (now().getDayOfWeek() == simulationSchedule.getDayOfWeek() && !time.isBefore(LocalTime.now())) {
                return now().withHour(time.getHour()).withMinute(time.getMinute());
            }
            return now()
                .with(TemporalAdjusters.next(simulationSchedule.getDayOfWeek()))
                .withHour(time.getHour())
                .withMinute(time.getMinute());
        }
    }

    private boolean verifySchedule(SimulationSchedule simulationSchedule) {
        if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.DAILY) {
            return simulationSchedule.getTimeOfDay() != null;
        } else if (simulationSchedule.getCycle() == SimulationSchedule.Cycle.WEEKLY) {
            return simulationSchedule.getTimeOfDay() != null && simulationSchedule.getDayOfWeek() != null;
        } else {
            return false;
        }
    }
}
