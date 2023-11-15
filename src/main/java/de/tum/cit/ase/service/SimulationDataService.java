package de.tum.cit.ase.service;

import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.repository.SimulationRepository;
import de.tum.cit.ase.repository.SimulationRunRepository;
import de.tum.cit.ase.util.ArtemisAccountDTO;
import de.tum.cit.ase.util.ArtemisServer;
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
    }

    public Simulation createSimulation(Simulation simulation) {
        simulation.setCreationDate(now());
        return simulationRepository.save(simulation);
    }

    public Simulation getSimulation(long id) {
        return simulationRepository.findById(id).orElseThrow();
    }

    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    public void deleteSimulation(long id) {
        simulationRepository.deleteById(id);
    }

    public void deleteSimulationRun(long runId) {
        simulationRunRepository.deleteById(runId);
    }

    public SimulationRun createAndQueueSimulationRun(long simulationId, ArtemisAccountDTO accountDTO) {
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
        simulationRun.setAdminAccount(accountDTO);

        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        simulationRunQueueService.queueSimulationRun(savedSimulationRun);
        return savedSimulationRun;
    }

    public boolean validateSimulation(Simulation simulation) {
        var basicRequirements =
            simulation.getNumberOfUsers() > 0 &&
            simulation.getMode() != null &&
            simulation.getServer() != null &&
            simulation.getName() != null;

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
}
