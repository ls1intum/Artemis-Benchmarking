package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.prometheus.MetricValue;
import de.tum.cit.ase.security.AuthoritiesConstants;
import de.tum.cit.ase.service.PrometheusService;
import de.tum.cit.ase.service.simulation.SimulationDataService;
import de.tum.cit.ase.util.ArtemisServer;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prometheus")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class PrometheusResource {

    private final PrometheusService prometheusService;
    private final SimulationDataService simulationDataService;

    public PrometheusResource(PrometheusService prometheusService, SimulationDataService simulationDataService) {
        this.simulationDataService = simulationDataService;
        this.prometheusService = prometheusService;
    }

    @GetMapping("/{server}/live")
    public ResponseEntity<List<MetricValue>> getLiveCpuUsage(@PathVariable("server") ArtemisServer server) {
        return ResponseEntity.ok(prometheusService.getLiveCpuUsage(server));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<List<MetricValue>> getCpuUsage(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsage(run));
    }
}
