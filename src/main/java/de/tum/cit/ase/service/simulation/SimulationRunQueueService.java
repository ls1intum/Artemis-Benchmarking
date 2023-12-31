package de.tum.cit.ase.service.simulation;

import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.repository.SimulationRunRepository;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimulationRunQueueService {

    private final Logger log = LoggerFactory.getLogger(SimulationRunQueueService.class);

    private final BlockingQueue<SimulationRun> simulationRunQueue;
    private final SimulationRunExecutionService simulationRunExecutionService;
    private final SimulationRunRepository simulationRunRepository;
    private Thread simulatorThread;
    private long currentRunId;

    public SimulationRunQueueService(
        SimulationRunExecutionService simulationRunExecutionService,
        SimulationRunRepository simulationRunRepository
    ) {
        this.simulationRunQueue = new LinkedBlockingQueue<>();
        this.simulationRunExecutionService = simulationRunExecutionService;
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
        return simulationRunQueue.remove(simulationRun);
    }

    /**
     * Infinite loop that takes simulation runs from the queue and executes them.
     * When no simulation runs are available, the thread is blocked until a new simulation run is added to the queue.
     */
    private void executeSimulationRun() {
        try {
            while (true) {
                var run = simulationRunQueue.take();
                simulationRunExecutionService.simulateExam(run);
                currentRunId = run.getId();
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
