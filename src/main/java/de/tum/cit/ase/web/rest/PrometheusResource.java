package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.prometheus.MetricValue;
import de.tum.cit.ase.security.AuthoritiesConstants;
import de.tum.cit.ase.service.PrometheusService;
import de.tum.cit.ase.service.simulation.SimulationDataService;
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

    @GetMapping("/{runId}/artemis")
    public ResponseEntity<List<MetricValue>> getCpuUsageArtemis(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsageArtemis(run));
    }

    @GetMapping("/{runId}/vcs")
    public ResponseEntity<List<MetricValue>> getCpuUsageVcs(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsageVcs(run));
    }

    @GetMapping("/{runId}/ci")
    public ResponseEntity<List<MetricValue>> getCpuUsageCi(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsageCi(run));
    }
}