package de.tum.cit.ase.service.simulation;

import static de.tum.cit.ase.util.ArtemisServer.PRODUCTION;
import static de.tum.cit.ase.util.NumberRangeParser.numberRangeRegex;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.repository.LogMessageRepository;
import de.tum.cit.ase.repository.SimulationRepository;
import de.tum.cit.ase.repository.SimulationRunRepository;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.util.ArtemisAccountDTO;
import de.tum.cit.ase.util.ArtemisServer;
import de.tum.cit.ase.util.NumberRangeParser;
import de.tum.cit.ase.web.websocket.SimulationWebsocketService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service to manage the data of simulations and simulation runs.
 */
@Service
public class SimulationDataService {

    private final Logger log = LoggerFactory.getLogger(SimulationDataService.class);

    private final SimulationRepository simulationRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationQueueService simulationQueueService;
    private final SimulationWebsocketService simulationWebsocketService;
    private final LogMessageRepository logMessageRepository;
    private final ArtemisConfiguration artemisConfiguration;

    public SimulationDataService(
        SimulationRepository simulationRepository,
        SimulationRunRepository simulationRunRepository,
        SimulationQueueService simulationQueueService,
        SimulationWebsocketService simulationWebsocketService,
        LogMessageRepository logMessageRepository,
        ArtemisConfiguration artemisConfiguration
    ) {
        this.simulationRepository = simulationRepository;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationQueueService = simulationQueueService;
        this.simulationWebsocketService = simulationWebsocketService;
        this.logMessageRepository = logMessageRepository;
        this.artemisConfiguration = artemisConfiguration;
    }

    /**
     * Create a new simulation.
     *
     * @param simulation the simulation to create
     * @return the created simulation
     * @throws IllegalArgumentException if the simulation is invalid
     */
    public Simulation createSimulation(Simulation simulation) {
        if (simulation == null) {
            throw new IllegalArgumentException("Simulation must not be null");
        }
        if (simulation.getId() != null) {
            throw new IllegalArgumentException("Simulation ID must be null");
        }
        if (simulation.getRuns() != null && !simulation.getRuns().isEmpty()) {
            throw new IllegalArgumentException("Simulation must not have any runs");
        }
        if (!validateSimulation(simulation)) {
            throw new IllegalArgumentException("Invalid simulation");
        }
        if (simulation.getIdeType() == null) {
            throw new IllegalArgumentException("IDE type must not be null");
        }
        // If only one of the instructor credentials is set, remove both
        if ((simulation.getInstructorUsername() != null) ^ (simulation.getInstructorPassword() != null)) {
            simulation.setInstructorUsername(null);
            simulation.setInstructorPassword(null);
        }

        simulation.setCreationDate(now());

        // If the user range is customized, calculate the number of users
        if (simulation.isCustomizeUserRange()) {
            var users = NumberRangeParser.parseNumberRange(simulation.getUserRange()).size();
            simulation.setNumberOfUsers(users);
        }

        // If the simulation is not running on the production server or is in a mode that does not require instructor credentials, remove them
        if (
            simulation.getServer() != PRODUCTION ||
            simulation.getMode() == Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM ||
            simulation.getMode() == Simulation.Mode.CREATE_COURSE_AND_EXAM
        ) {
            simulation.setInstructorUsername(null);
            simulation.setInstructorPassword(null);
        }
        return simulationRepository.save(simulation);
    }

    /**
     * Get an existing simulation.
     *
     * @param id the id of the simulation to get
     * @return the simulation
     * @throws NoSuchElementException if the simulation is invalid
     */
    public Simulation getSimulation(long id) {
        return simulationRepository.findById(id).orElseThrow();
    }

    /**
     * Get all simulations.
     *
     * @return a list of all simulations
     */
    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    /**
     * Get a simulation run.
     *
     * @param id the id of the simulation run to get
     * @return the simulation run
     * @throws NoSuchElementException if the simulation run is invalid
     */
    public SimulationRun getSimulationRun(long id) {
        return simulationRunRepository.findById(id).orElseThrow();
    }

    /**
     * Get a simulation run with its results and log messages.
     *
     * @param id the id of the simulation run to get
     * @return a run with its results and log messages
     */
    public SimulationRun getSimulationRunWithStatsAndLogs(long id) {
        return simulationRunRepository.findByIdWithStatsAndLogMessages(id).orElseThrow();
    }

    /**
     * Delete a simulation.
     *
     * @param id the id of the simulation to delete
     * @throws IllegalArgumentException if the simulation has a running simulation run
     */
    public void deleteSimulation(long id) {
        if (simulationRunRepository.findAllBySimulationId(id).stream().anyMatch(run -> run.getStatus() == SimulationRun.Status.RUNNING)) {
            throw new IllegalArgumentException("Cannot delete a simulation with a running simulation run!");
        }
        simulationRepository.deleteById(id);
    }

    /**
     * Delete a simulation run.
     *
     * @param runId the id of the simulation run to delete
     * @throws IllegalArgumentException if the simulation run is running
     * @throws NoSuchElementException if the simulation run does not exist
     */
    public void deleteSimulationRun(long runId) {
        var run = simulationRunRepository.findById(runId).orElseThrow();
        if (run.getStatus() == SimulationRun.Status.RUNNING) {
            throw new IllegalArgumentException("Cannot delete a running simulation run!");
        }
        if (run.getStatus() == SimulationRun.Status.QUEUED) {
            simulationQueueService.removeSimulationRunFromQueue(run);
        }
        simulationRunRepository.deleteById(runId);
    }

    /**
     * Create and queue a new simulation run.
     *
     * @param simulationId the id of the simulation to run
     * @param accountDTO the admin / instructor account to use for the simulation (only required for production instance)
     * @param schedule the schedule which caused the execution of the simulation run (optional)
     * @return the created and queued simulation run
     * @throws IllegalArgumentException if the simulation mode requires an admin / instructor account and none is provided
     * @throws NoSuchElementException if the simulation does not exist
     */
    public SimulationRun createAndQueueSimulationRun(long simulationId, ArtemisAccountDTO accountDTO, SimulationSchedule schedule) {
        Simulation simulation = simulationRepository.findById(simulationId).orElseThrow();

        if (
            simulation.getServer() == PRODUCTION &&
            simulation.getMode() != Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM &&
            !simulation.instructorCredentialsProvided() &&
            accountDTO == null
        ) {
            throw new IllegalArgumentException("This simulation mode requires an admin / instructor account!");
        }

        SimulationRun simulationRun = new SimulationRun();
        simulationRun.setSimulation(simulation);
        simulationRun.setStats(new HashSet<>());
        simulationRun.setLogMessages(new HashSet<>());
        simulationRun.setStatus(SimulationRun.Status.QUEUED);
        simulationRun.setStartDateTime(now());

        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        savedSimulationRun.setAdminAccount(accountDTO);
        savedSimulationRun.setSchedule(schedule);
        simulationQueueService.queueSimulationRun(savedSimulationRun);
        simulationWebsocketService.sendNewRun(savedSimulationRun);
        return savedSimulationRun;
    }

    /**
     * Validate a simulation.
     *
     * @param simulation the simulation to validate
     * @return true if the simulation is valid, false otherwise
     */
    public boolean validateSimulation(Simulation simulation) {
        var basicRequirements =
            simulation.getMode() != null &&
            simulation.getServer() != null &&
            simulation.getNumberOfCommitsAndPushesTo() > simulation.getNumberOfCommitsAndPushesFrom() &&
            simulation.getNumberOfCommitsAndPushesFrom() >= 0 &&
            simulation.getName() != null &&
            ((!simulation.isCustomizeUserRange() && simulation.getNumberOfUsers() > 0) ||
                (simulation.isCustomizeUserRange() &&
                    simulation.getUserRange() != null &&
                    simulation.getUserRange().matches(numberRangeRegex)));

        return (
            basicRequirements &&
            switch (simulation.getMode()) {
                case CREATE_COURSE_AND_EXAM -> true;
                case EXISTING_COURSE_UNPREPARED_EXAM, EXISTING_COURSE_PREPARED_EXAM -> simulation.getCourseId() > 0 &&
                simulation.getExamId() > 0;
                case EXISTING_COURSE_CREATE_EXAM -> simulation.getCourseId() > 0;
            }
        );
    }

    /**
     * Cancel the currently active simulation run.
     * @param runId the ID of the simulation run to cancel
     * @throws IllegalArgumentException if run with given ID is not active
     */
    public void cancelActiveRun(long runId) {
        var run = simulationRunRepository.findById(runId).orElseThrow();
        if (run.getStatus() != SimulationRun.Status.RUNNING) {
            throw new IllegalArgumentException("Simulation run is not active!");
        } else {
            log.info("Cancelling simulation run {}", runId);

            simulationQueueService.abortSimulationExecution();
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            run = simulationRunRepository.findByIdWithStatsAndLogMessages(runId).orElseThrow();

            run.setStatus(SimulationRun.Status.CANCELLED);
            run.setEndDateTime(now());
            simulationWebsocketService.sendRunStatusUpdate(run);

            var logMsg = new LogMessage();
            logMsg.setTimestamp(now());
            logMsg.setMessage("Run cancelled");
            logMsg.setSimulationRun(run);
            logMsg.setError(true);
            run.getLogMessages().add(logMsg);
            simulationWebsocketService.sendRunLogMessage(run, logMsg);
            log.info("Simulation run {} cancelled", runId);
            simulationRunRepository.save(run);
            logMessageRepository.save(logMsg);

            simulationQueueService.restartSimulationExecution();
        }
    }

    /**
     * Get a list of all Artemis servers that have cleanup enabled.
     * @return a list of all Artemis servers that have cleanup enabled
     */
    public List<ArtemisServer> getServersWithCleanupEnabled() {
        List<ArtemisServer> servers = new ArrayList<>();
        for (ArtemisServer server : ArtemisServer.values()) {
            if (artemisConfiguration.getCleanup(server)) {
                servers.add(server);
            }
        }
        return servers;
    }

    /**
     * Update the instructor account for a simulation.
     * @param simulationId the ID of the simulation to update
     * @param account the new instructor account
     * @return the updated simulation
     */
    public Simulation updateInstructorAccount(long simulationId, ArtemisAccountDTO account) {
        var simulation = simulationRepository.findById(simulationId).orElseThrow();
        if (simulation.getServer() != PRODUCTION) {
            log.warn("Cannot update instructor account for simulation {} because it is not running on production server", simulationId);
            return simulation;
        }
        if (
            simulation.getMode() != Simulation.Mode.EXISTING_COURSE_CREATE_EXAM &&
            simulation.getMode() != Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM
        ) {
            log.warn(
                "Cannot update instructor account for simulation {} because it is not in a mode that requires an instructor account",
                simulationId
            );
            return simulation;
        }
        simulation.setInstructorUsername(account.getUsername());
        simulation.setInstructorPassword(account.getPassword());
        return simulationRepository.save(simulation);
    }

    /**
     * Remove the instructor account for a simulation.
     * @param simulationId the ID of the simulation to update
     * @return the updated simulation
     */
    public Simulation removeInstructorAccount(long simulationId) {
        var simulation = simulationRepository.findById(simulationId).orElseThrow();

        simulation.setInstructorUsername(null);
        simulation.setInstructorPassword(null);
        return simulationRepository.save(simulation);
    }
}
