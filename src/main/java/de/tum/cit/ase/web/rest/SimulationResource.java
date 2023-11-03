package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.security.AuthoritiesConstants;
import de.tum.cit.ase.service.SimulationService;
import de.tum.cit.ase.service.artemis.ArtemisServer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class SimulationResource {

    private final SimulationService simulationService;

    public SimulationResource(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public ResponseEntity<Void> startSimulation(
        @RequestParam(value = "users") int numberOfUsers,
        @RequestParam(value = "courseId") int courseId,
        @RequestParam(value = "examId") int examId,
        @RequestParam(value = "server") ArtemisServer server
    ) {
        if (numberOfUsers <= 0 || courseId < 0 || examId < 0 || server == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Either both zero or both non-zero
        if ((courseId == 0) ^ (examId == 0)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        simulationService.simulateExam(numberOfUsers, courseId, examId, server);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
