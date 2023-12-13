package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.service.simulation.SimulationScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/schedules")
public class PublicScheduleResource {

    private final SimulationScheduleService simulationScheduleService;

    public PublicScheduleResource(SimulationScheduleService simulationScheduleService) {
        this.simulationScheduleService = simulationScheduleService;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteByKey(@RequestParam("key") String key) {
        simulationScheduleService.unsubscribeFromSchedule(key);
        return ResponseEntity.ok().build();
    }
}
