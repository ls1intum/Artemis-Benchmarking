package de.tum.cit.ase.service;

import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.repository.SimulationRepository;
import de.tum.cit.ase.repository.SimulationRunRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SimulationDataService {

    private final SimulationRepository simulationRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationRunQueueService simulationRunQueueService;

    public SimulationDataService(
        SimulationRepository simulationRepository,
        SimulationRunRepository simulationRunRepository,
        SimulationRunQueueService simulationRunQueueService
    ) {
        this.simulationRepository = simulationRepository;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationRunQueueService = simulationRunQueueService;

        // TODO: REMOVE THIS
        createAndQueueSimulationRun(5);
    }

    public Simulation createSimulation(Simulation simulation) {
        return simulationRepository.save(simulation);
    }

    public Simulation getSimulation(long id) {
        return simulationRepository.findById(id).orElseThrow();
    }

    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    public SimulationRun createAndQueueSimulationRun(long simulationId) {
        Simulation simulation = simulationRepository.findById(simulationId).orElseThrow();

        SimulationRun simulationRun = new SimulationRun();
        simulationRun.setSimulation(simulation);
        simulationRun.setStats(new HashSet<>());
        simulationRun.setLogMessages(new HashSet<>());
        simulationRun.setStatus(SimulationRun.Status.QUEUED);
        simulationRun.setStartDateTime(now());

        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        simulationRunQueueService.queueSimulationRun(savedSimulationRun);
        return savedSimulationRun;
    }
}
