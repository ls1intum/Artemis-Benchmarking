package de.tum.cit.ase.service.simulation;

import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.repository.SimulationRunRepository;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.stereotype.Service;

@Service
public class SimulationRunQueueService {

    private final BlockingQueue<SimulationRun> simulationRunQueue;
    private final SimulationRunExecutionService simulationRunExecutionService;
    private final SimulationRunRepository simulationRunRepository;

    public SimulationRunQueueService(
        SimulationRunExecutionService simulationRunExecutionService,
        SimulationRunRepository simulationRunRepository
    ) {
        this.simulationRunQueue = new LinkedBlockingQueue<>();
        this.simulationRunExecutionService = simulationRunExecutionService;
        this.simulationRunRepository = simulationRunRepository;
        initializeSimulationRunQueue();
        new Thread(this::executeSimulationRun).start();
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
     * Infinite loop that takes simulation runs from the queue and executes them.
     * When no simulation runs are available, the thread is blocked until a new simulation run is added to the queue.
     */
    private void executeSimulationRun() {
        try {
            while (true) {
                SimulationRun simulationRun;
                simulationRun = simulationRunQueue.take();
                simulationRunExecutionService.simulateExam(simulationRun);
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
