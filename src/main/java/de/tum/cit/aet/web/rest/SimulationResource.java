package de.tum.cit.aet.web.rest;

import de.tum.cit.aet.domain.Simulation;
import de.tum.cit.aet.domain.SimulationRun;
import de.tum.cit.aet.domain.SimulationSchedule;
import de.tum.cit.aet.security.AuthoritiesConstants;
import de.tum.cit.aet.service.simulation.SimulationDataService;
import de.tum.cit.aet.service.simulation.SimulationScheduleService;
import de.tum.cit.aet.util.ArtemisAccountDTO;
import de.tum.cit.aet.util.ArtemisServer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulations")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class SimulationResource {

    private static final Logger log = LoggerFactory.getLogger(SimulationResource.class);

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
        var savedSimulation = simulationDataService.createSimulation(simulation);

        // Never return the instructor username and password to the client
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

        // Never return the instructor username and password to the client
        simulations.forEach(simulation -> {
            if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
                simulation.setInstructorUsername("");
                simulation.setInstructorPassword("");
            }
            simulation.getRuns().forEach(SimulationResource::sanitizeSimulationRun);
        });

        log.info("Return {} simulations", simulations.size());
        return new ResponseEntity<>(simulations, HttpStatus.OK);
    }

    private static void sanitizeSimulationRun(SimulationRun run) {
        // Reduce payload by not returning the simulation and log messages for each run
        // TODO: use DTOs to control the returned fields
        run.setSimulation(null);
        run.setLogMessages(null);
        run.setStats(null);
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

        // Never return the instructor username and password to the client
        if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
            simulation.setInstructorUsername("");
            simulation.setInstructorPassword("");
        }
        simulation.getRuns().forEach(SimulationResource::sanitizeSimulationRun);
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
        return new ResponseEntity<>(simulationDataService.getSimulationRunWithStatsAndLogs(runId), HttpStatus.OK);
    }

    /**
     * POST /api/simulations/{simulationId}/run : Create and queue a new run for the given simulation.
     *
     * @param simulationId the ID of the simulation to run
     * @param accountDTO the account to use for running the simulation
     * @return the ResponseEntity with status 200 (OK) and with body the queued simulation run, or with status 404 (Not Found) if the simulation does not exist
     */
    @PostMapping("/{simulationId}/run")
    public ResponseEntity<SimulationRun> runSimulation(
        @PathVariable long simulationId,
        @RequestBody(required = false) ArtemisAccountDTO accountDTO
    ) {
        var run = simulationDataService.createAndQueueSimulationRun(simulationId, accountDTO, null);
        sanitizeSimulationRun(run);
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

    /**
     * POST /api/simulations/{simulationId}/schedule : Create a schedule for a simulation.
     *
     * @param simulationId the ID of the simulation to schedule
     * @param simulationSchedule the schedule to create
     * @return the ResponseEntity with status 200 (OK) and with body the created schedule
     */
    @PostMapping("/{simulationId}/schedule")
    public ResponseEntity<SimulationSchedule> scheduleSimulation(
        @PathVariable long simulationId,
        @RequestBody SimulationSchedule simulationSchedule
    ) {
        var schedule = simulationScheduleService.createSimulationSchedule(simulationId, simulationSchedule);
        return new ResponseEntity<>(schedule, HttpStatus.OK);
    }

    /**
     * PUT /api/schedules/{scheduleId} : Update a schedule.
     *
     * @param scheduleId the ID of the schedule to update
     * @param simulationSchedule the updated schedule
     * @return the ResponseEntity with status 200 (OK) and with body the updated schedule
     */
    @PutMapping("/schedules/{scheduleId}")
    public ResponseEntity<SimulationSchedule> updateSchedule(
        @PathVariable long scheduleId,
        @RequestBody SimulationSchedule simulationSchedule
    ) {
        var schedule = simulationScheduleService.updateSimulationSchedule(scheduleId, simulationSchedule);
        return new ResponseEntity<>(schedule, HttpStatus.OK);
    }

    /**
     * DELETE /api/schedules/{scheduleId} : Delete a schedule.
     *
     * @param scheduleId the ID of the schedule to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable long scheduleId) {
        simulationScheduleService.deleteSimulationSchedule(scheduleId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * GET /api/simulations/{simulationId}/schedules : Get all schedules for a simulation.
     *
     * @param simulationId the ID of the simulation to get schedules for
     * @return the ResponseEntity with status 200 (OK) and with body the list of schedules
     */
    @GetMapping("/{simulationId}/schedules")
    public ResponseEntity<List<SimulationSchedule>> getSchedules(@PathVariable long simulationId) {
        return new ResponseEntity<>(simulationScheduleService.getSimulationSchedules(simulationId), HttpStatus.OK);
    }

    /**
     * POST /api/schedules/{scheduleId}/subscribe : Subscribe to a schedule.
     *
     * @param scheduleId the ID of the schedule to subscribe to
     * @param email the email to subscribe
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/schedules/{scheduleId}/subscribe")
    public ResponseEntity<Void> subscribeToSchedule(@PathVariable long scheduleId, @RequestBody String email) {
        simulationScheduleService.subscribeToSchedule(scheduleId, email);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * PATCH /api/{simulationId}/instructor-account : Update the instructor account for a simulation.
     *
     * @param simulationId the ID of the simulation to update the instructor account for
     * @param account the updated account
     * @return the ResponseEntity with status 200 (OK) and with body the updated simulation
     */
    @PatchMapping("/{simulationId}/instructor-account")
    public ResponseEntity<Simulation> updateInstructorAccount(@PathVariable long simulationId, @RequestBody ArtemisAccountDTO account) {
        var simulation = simulationDataService.updateInstructorAccount(simulationId, account);

        // Never return the instructor username and password to the client
        if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
            simulation.setInstructorUsername("");
            simulation.setInstructorPassword("");
        }
        return new ResponseEntity<>(simulation, HttpStatus.OK);
    }

    /**
     * DELETE /api/{simulationId}/instructor-account : Remove the instructor account for a simulation.
     *
     * @param simulationId the ID of the simulation to remove the instructor account for
     * @return the ResponseEntity with status 200 (OK) and with body the updated simulation
     */
    @DeleteMapping("/{simulationId}/instructor-account")
    public ResponseEntity<Simulation> removeInstructorAccount(@PathVariable long simulationId) {
        var simulation = simulationDataService.removeInstructorAccount(simulationId);

        // Never return the instructor username and password to the client
        if (simulation.getInstructorUsername() != null || simulation.getInstructorPassword() != null) {
            simulation.setInstructorUsername("");
            simulation.setInstructorPassword("");
        }
        return new ResponseEntity<>(simulation, HttpStatus.OK);
    }
}
