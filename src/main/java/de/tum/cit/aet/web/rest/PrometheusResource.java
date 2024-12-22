package de.tum.cit.aet.web.rest;

import de.tum.cit.aet.domain.SimulationRun;
import de.tum.cit.aet.prometheus.Metric;
import de.tum.cit.aet.security.AuthoritiesConstants;
import de.tum.cit.aet.service.PrometheusService;
import de.tum.cit.aet.service.artemis.ArtemisConfiguration;
import de.tum.cit.aet.service.simulation.SimulationDataService;
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
    private final ArtemisConfiguration artemisConfiguration;

    public PrometheusResource(
        PrometheusService prometheusService,
        SimulationDataService simulationDataService,
        ArtemisConfiguration artemisConfiguration
    ) {
        this.simulationDataService = simulationDataService;
        this.prometheusService = prometheusService;
        this.artemisConfiguration = artemisConfiguration;
    }

    /**
     * Get the CPU usage of the Artemis instance for the given run.
     * @param runId The id of the run to get the CPU usage for.
     * @return A list of CPU usage values. An empty list if no Prometheus instance is configured for Artemis.
     */
    @GetMapping("/{runId}/artemis")
    public ResponseEntity<List<Metric>> getCpuUsageArtemis(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsageArtemis(run));
    }

    /**
     * Get the CPU usage of the VCS for the given run.
     * @param runId The id of the run to get the CPU usage for.
     * @return A list of CPU usage values. An empty list if no Prometheus instance is configured for the VCS.
     */
    @GetMapping("/{runId}/vcs")
    public ResponseEntity<List<Metric>> getCpuUsageVcs(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        if (artemisConfiguration.getIsLocal(run.getSimulation().getServer())) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsageVcs(run));
    }

    /**
     * Get the CPU usage of the CI for the given run.
     * @param runId The id of the run to get the CPU usage for.
     * @return A list of CPU usage values. An empty list if no Prometheus instance is configured for the CI.
     */
    @GetMapping("/{runId}/ci")
    public ResponseEntity<List<Metric>> getCpuUsageCi(@PathVariable("runId") long runId) {
        var run = simulationDataService.getSimulationRun(runId);
        if (run.getStatus() != SimulationRun.Status.FINISHED && run.getStatus() != SimulationRun.Status.RUNNING) {
            return ResponseEntity.ok(List.of());
        }
        if (artemisConfiguration.getIsLocal(run.getSimulation().getServer())) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(prometheusService.getCpuUsageCi(run));
    }
}
