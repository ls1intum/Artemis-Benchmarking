package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.domain.Simulation;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.security.AuthoritiesConstants;
import de.tum.cit.ase.service.simulation.SimulationDataService;
import de.tum.cit.ase.util.ArtemisAccountDTO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulations")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class SimulationResource {

    private final SimulationDataService simulationDataService;

    public SimulationResource(SimulationDataService simulationService) {
        this.simulationDataService = simulationService;
    }

    /**
     * POST /api/simulations : Create a new simulation (without starting it).
     *
     * @param simulation the simulation to create
     * @return the ResponseEntity with status 200 (OK) and with body the new simulation, or with status 400 (Bad Request) if the simulation is invalid
     */
    @PostMapping
    public ResponseEntity<Simulation> createSimulation(@RequestBody Simulation simulation) {
        if (simulation == null) {
            throw new IllegalArgumentException("Simulation must not be null");
        }
        if (simulation.getId() != null) {
            throw new IllegalArgumentException("Simulation ID must be null");
        }
        if (simulation.getRuns() != null && !simulation.getRuns().isEmpty()) {
            throw new IllegalArgumentException("Simulation must not have any runs");
        }
        if (!simulationDataService.validateSimulation(simulation)) {
            throw new IllegalArgumentException("Invalid simulation");
        }
        return new ResponseEntity<>(simulationDataService.createSimulation(simulation), HttpStatus.OK);
    }

    /**
     * GET /api/simulations : Get all simulations.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of simulations
     */
    @GetMapping
    public ResponseEntity<List<Simulation>> getAllSimulations() {
        return new ResponseEntity<>(simulationDataService.getAllSimulations(), HttpStatus.OK);
    }

    /**
     * GET /api/simulations/{simulationId} : Get a simulation.
     *
     * @param simulationId the ID of the simulation to get
     * @return the ResponseEntity with status 200 (OK) and with body the simulation, or with status 404 (Not Found) if the simulation does not exist
     */
    @GetMapping("/{simulationId}")
    public ResponseEntity<Simulation> getSimulation(@PathVariable long simulationId) {
        return new ResponseEntity<>(simulationDataService.getSimulation(simulationId), HttpStatus.OK);
    }

    /**
     * GET /api/simulations/runs/{runId} : Get a simulation run.
     *
     * @param runId the ID of the run to get
     * @return the ResponseEntity with status 200 (OK) and with body the simulation run, or with status 404 (Not Found) if the run does not exist
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<SimulationRun> getSimulationRun(@PathVariable long runId) {
        return new ResponseEntity<>(simulationDataService.getSimulationRun(runId), HttpStatus.OK);
    }

    /**
     * POST /api/simulations/{simulationId}/run : Create and queue a new run for the given simulation.
     *
     * @param simulationId the ID of the simulation to run
     * @return the ResponseEntity with status 200 (OK) and with body the queued simulation run, or with status 404 (Not Found) if the simulation does not exist
     */
    @PostMapping("/{simulationId}/run")
    public ResponseEntity<SimulationRun> runSimulation(
        @PathVariable long simulationId,
        @RequestBody(required = false) ArtemisAccountDTO accountDTO
    ) {
        var run = simulationDataService.createAndQueueSimulationRun(simulationId, accountDTO);
        return new ResponseEntity<>(run, HttpStatus.OK);
    }

    /**
     * DELETE /api/simulations/{simulationId} : Delete a simulation.
     * All runs of the simulation and their associated results and logs will be deleted as well.
     * If the given simulation does not exist, nothing will happen.
     *
     * @param simulationId the ID of the simulation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/{simulationId}")
    public ResponseEntity<Void> deleteSimulation(@PathVariable long simulationId) {
        simulationDataService.deleteSimulation(simulationId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * DELETE /api/simulations/runs/{runId} : Delete a simulation run.
     * All results and logs of the run will be deleted as well.
     * If the given run does not exist, nothing will happen.
     *
     * @param runId the ID of the run to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/runs/{runId}")
    public ResponseEntity<Void> deleteSimulationRun(@PathVariable long runId) {
        simulationDataService.deleteSimulationRun(runId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
