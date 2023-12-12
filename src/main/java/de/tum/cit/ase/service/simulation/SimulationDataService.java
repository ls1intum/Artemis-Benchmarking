package de.tum.cit.ase.service.simulation;

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

@Service
public class SimulationDataService {

    private final Logger log = LoggerFactory.getLogger(SimulationDataService.class);

    private final SimulationRepository simulationRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationRunQueueService simulationRunQueueService;
    private final SimulationWebsocketService simulationWebsocketService;
    private final LogMessageRepository logMessageRepository;
    private final ArtemisConfiguration artemisConfiguration;

    public SimulationDataService(
        SimulationRepository simulationRepository,
        SimulationRunRepository simulationRunRepository,
        SimulationRunQueueService simulationRunQueueService,
        SimulationWebsocketService simulationWebsocketService,
        LogMessageRepository logMessageRepository,
        ArtemisConfiguration artemisConfiguration
    ) {
        this.simulationRepository = simulationRepository;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationRunQueueService = simulationRunQueueService;
        this.simulationWebsocketService = simulationWebsocketService;
        this.logMessageRepository = logMessageRepository;
        this.artemisConfiguration = artemisConfiguration;
    }

    public Simulation createSimulation(Simulation simulation) {
        simulation.setCreationDate(now());
        if (simulation.isCustomizeUserRange()) {
            var users = NumberRangeParser.parseNumberRange(simulation.getUserRange()).size();
            simulation.setNumberOfUsers(users);
        }
        return simulationRepository.save(simulation);
    }

    public Simulation getSimulation(long id) {
        return simulationRepository.findById(id).orElseThrow();
    }

    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    public SimulationRun getSimulationRun(long id) {
        return simulationRunRepository.findById(id).orElseThrow();
    }

    public void deleteSimulation(long id) {
        if (simulationRunRepository.findAllBySimulationId(id).stream().anyMatch(run -> run.getStatus() == SimulationRun.Status.RUNNING)) {
            throw new IllegalArgumentException("Cannot delete a simulation with a running simulation run!");
        }
        simulationRepository.deleteById(id);
    }

    public void deleteSimulationRun(long runId) {
        var run = simulationRunRepository.findById(runId).orElseThrow();
        if (run.getStatus() == SimulationRun.Status.RUNNING) {
            throw new IllegalArgumentException("Cannot delete a running simulation run!");
        }
        if (run.getStatus() == SimulationRun.Status.QUEUED) {
            simulationRunQueueService.removeSimulationRunFromQueue(run);
        }
        simulationRunRepository.deleteById(runId);
    }

    public SimulationRun createAndQueueSimulationRun(long simulationId, ArtemisAccountDTO accountDTO, SimulationSchedule schedule) {
        Simulation simulation = simulationRepository.findById(simulationId).orElseThrow();

        if (
            simulation.getServer() == ArtemisServer.PRODUCTION &&
            simulation.getMode() != Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM &&
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
        simulationRunQueueService.queueSimulationRun(savedSimulationRun);
        simulationWebsocketService.sendNewRun(savedSimulationRun);
        return savedSimulationRun;
    }

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

            simulationRunQueueService.abortSimulationExecution();
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            run = simulationRunRepository.findById(runId).orElseThrow();

            run.setStatus(SimulationRun.Status.CANCELLED);
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

            simulationRunQueueService.restartSimulationExecution();
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
}
