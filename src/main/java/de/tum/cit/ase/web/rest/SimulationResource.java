package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.domain.Simulation;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.domain.SimulationSchedule;
import de.tum.cit.ase.security.AuthoritiesConstants;
import de.tum.cit.ase.service.simulation.SimulationDataService;
import de.tum.cit.ase.service.simulation.SimulationScheduleService;
import de.tum.cit.ase.util.ArtemisAccountDTO;
import de.tum.cit.ase.util.ArtemisServer;
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
    private final SimulationScheduleService simulationScheduleService;

    public SimulationResource(SimulationDataService simulationService, SimulationScheduleService simulationScheduleService) {
        this.simulationDataService = simulationService;
        this.simulationScheduleService = simulationScheduleService;
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
        // If only one of the instructor credentials is set, remove both
        if ((simulation.getInstructorUsername() != null) ^ (simulation.getInstructorPassword() != null)) {
            simulation.setInstructorUsername(null);
            simulation.setInstructorPassword(null);
        }
        var savedSimulation = simulationDataService.createSimulation(simulation);
        if (savedSimulation.getInstructorUsername() != null || savedSimulation.getInstructorPassword() != null) {
            savedSimulation.setInstructorUsername("");
            savedSimulation.setInstructorPassword("");
        }
        return new ResponseEntity<>(savedSimulation, HttpStatus.OK);
    }

    /**
     * GET /api/simulations : Get all simulations.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of simulations
     */
    @GetMapping
    public ResponseEntity<List<Simulation>> getAllSimulations() {
        var simulations = simulationDataService.getAllSimulations();
        simulations.forEach(simulation -> {
            if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
                simulation.setInstructorUsername("");
                simulation.setInstructorPassword("");
            }
        });
        return new ResponseEntity<>(simulations, HttpStatus.OK);
    }

    /**
     * GET /api/simulations/{simulationId} : Get a simulation.
     *
     * @param simulationId the ID of the simulation to get
     * @return the ResponseEntity with status 200 (OK) and with body the simulation, or with status 404 (Not Found) if the simulation does not exist
     */
    @GetMapping("/{simulationId}")
    public ResponseEntity<Simulation> getSimulation(@PathVariable long simulationId) {
        var simulation = simulationDataService.getSimulation(simulationId);
        if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
            simulation.setInstructorUsername("");
            simulation.setInstructorPassword("");
        }
        return new ResponseEntity<>(simulation, HttpStatus.OK);
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
        var run = simulationDataService.createAndQueueSimulationRun(simulationId, accountDTO, null);
        return new ResponseEntity<>(run, HttpStatus.OK);
    }

    /**
     * POST /api/simulations/runs/{runId}/abort : Abort a simulation run.
     * @param runId the ID of the run to abort
     * @return the ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the run does not exist or with status 400 (Bad Request) if the run is not running
     */
    @PostMapping("/runs/{runId}/abort")
    public ResponseEntity<Void> abortSimulationRun(@PathVariable long runId) {
        simulationDataService.cancelActiveRun(runId);
        return new ResponseEntity<>(HttpStatus.OK);
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

    /**
     * GET /api/servers/cleanup-enabled : Get all servers for which cleanup is enabled.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of servers
     */
    @GetMapping("/servers/cleanup-enabled")
    public ResponseEntity<List<ArtemisServer>> getServersWithCleanupEnabled() {
        return new ResponseEntity<>(simulationDataService.getServersWithCleanupEnabled(), HttpStatus.OK);
    }

    @PostMapping("/{simulationId}/schedule")
    public ResponseEntity<SimulationSchedule> scheduleSimulation(
        @PathVariable long simulationId,
        @RequestBody SimulationSchedule simulationSchedule
    ) {
        var schedule = simulationScheduleService.createSimulationSchedule(simulationId, simulationSchedule);
        return new ResponseEntity<>(schedule, HttpStatus.OK);
    }

    @PutMapping("/schedules/{scheduleId}")
    public ResponseEntity<SimulationSchedule> updateSchedule(
        @PathVariable long scheduleId,
        @RequestBody SimulationSchedule simulationSchedule
    ) {
        var schedule = simulationScheduleService.updateSimulationSchedule(scheduleId, simulationSchedule);
        return new ResponseEntity<>(schedule, HttpStatus.OK);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable long scheduleId) {
        simulationScheduleService.deleteSimulationSchedule(scheduleId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/{simulationId}/schedules")
    public ResponseEntity<List<SimulationSchedule>> getSchedules(@PathVariable long simulationId) {
        return new ResponseEntity<>(simulationScheduleService.getSimulationSchedules(simulationId), HttpStatus.OK);
    }

    @PostMapping("/schedules/{scheduleId}/subscribe")
    public ResponseEntity<Void> subscribeToSchedule(@PathVariable long scheduleId, @RequestBody String email) {
        simulationScheduleService.subscribeToSchedule(scheduleId, email);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PatchMapping("/{simulationId}/instructor-account")
    public ResponseEntity<Simulation> updateInstructorAccount(@PathVariable long simulationId, @RequestBody ArtemisAccountDTO account) {
        var simulation = simulationDataService.updateInstructorAccount(simulationId, account);
        if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
            simulation.setInstructorUsername("");
            simulation.setInstructorPassword("");
        }
        return new ResponseEntity<>(simulation, HttpStatus.OK);
    }
}
