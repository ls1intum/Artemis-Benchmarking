package de.tum.cit.aet.service.simulation;

import de.tum.cit.aet.domain.SimulationRun;
import de.tum.cit.aet.repository.SimulationRunRepository;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service to manage the queue of simulation runs.
 */
@Service
public class SimulationQueueService {

    private final Logger log = LoggerFactory.getLogger(SimulationQueueService.class);

    private final BlockingQueue<SimulationRun> simulationRunQueue;
    private final SimulationExecutionService simulationExecutionService;
    private final SimulationRunRepository simulationRunRepository;
    private Thread simulatorThread;

    public SimulationQueueService(SimulationExecutionService simulationExecutionService, SimulationRunRepository simulationRunRepository) {
        this.simulationRunQueue = new LinkedBlockingQueue<>();
        this.simulationExecutionService = simulationExecutionService;
        this.simulationRunRepository = simulationRunRepository;
        initializeSimulationRunQueue();
        restartSimulationExecution();
    }

    /**
     * Add a simulation run to the end of the queue.
     * <p>
     * Note: The status of the simulation run will not be updated here.
     * Make sure to update the status before adding the simulation run to the queue.
     * @param simulationRun the simulation run to add to the queue
     */
    public void queueSimulationRun(SimulationRun simulationRun) {
        simulationRunQueue.add(simulationRun);
    }

    /**
     * Abort the Thread that executes the simulation runs. That results in the current simulation run being aborted.
     */
    public synchronized void abortSimulationExecution() {
        if (simulatorThread == null) {
            throw new IllegalStateException("Simulation execution is not running");
        }
        log.info("Aborting simulation execution");
        simulatorThread.interrupt();
        simulatorThread = null;
    }

    /**
     * Start a new Thread that executes the simulation runs.
     */
    public synchronized void restartSimulationExecution() {
        if (simulatorThread != null) {
            throw new IllegalStateException("Simulation execution is already running");
        }
        log.info("Starting simulation execution");
        simulatorThread = new Thread(this::executeSimulationRun);
        simulatorThread.start();
    }

    public boolean removeSimulationRunFromQueue(SimulationRun simulationRun) {
        var result = simulationRunQueue.removeIf(r -> Objects.equals(r.getId(), simulationRun.getId()));
        if (!result) {
            log.warn("Could not remove simulation run {} from queue", simulationRun.getId());
        } else {
            log.info("Removed simulation run {} from queue", simulationRun.getId());
        }
        return result;
    }

    /**
     * Infinite loop that takes simulation runs from the queue and executes them.
     * When no simulation runs are available, the thread is blocked until a new simulation run is added to the queue.
     */
    private void executeSimulationRun() {
        try {
            while (true) {
                var run = simulationRunQueue.take();
                try {
                    simulationExecutionService.simulateExam(run);
                } catch (Exception e) {
                    log.error("Error while executing simulation run", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Add all queued simulation runs from the database to the queue in the correct order.
     */
    private void initializeSimulationRunQueue() {
        simulationRunRepository
            .findAllByStatus(SimulationRun.Status.QUEUED)
            .stream()
            .sorted(Comparator.comparing(SimulationRun::getStartDateTime))
            .forEach(this::queueSimulationRun);
    }
}
