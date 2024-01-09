package de.tum.cit.ase.service;

import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.prometheus.MetricValue;
import de.tum.cit.ase.prometheus.QueryResponse;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PrometheusService {

    private final Logger log = LoggerFactory.getLogger(PrometheusService.class);

    @Value("${prometheus.api-url}")
    private String baseUrl;

    @Value("${prometheus.auth-token}")
    private String authToken;

    @Value("${prometheus.resolution}")
    private int resolution;

    private WebClient webClient;
    private final ArtemisConfiguration artemisConfiguration;

    public PrometheusService(ArtemisConfiguration artemisConfiguration) {
        this.artemisConfiguration = artemisConfiguration;
    }

    /**
     * Get the CPU usage of the Artemis instance for the given run.
     * @param run The run to get the CPU usage for.
     * @return A list of CPU usage values. An empty list if no Prometheus instance is configured for Artemis.
     */
    public List<MetricValue> getCpuUsageArtemis(SimulationRun run) {
        log.info("Getting Artemis CPU usage for {}", run);
        var instance = artemisConfiguration.getPrometheusInstanceArtemis(run.getSimulation().getServer());
        if (instance == null || instance.isBlank()) {
            log.warn("No Prometheus instance configured for Artemis on {}", run.getSimulation().getServer());
            return List.of();
        }
        return getCpuUsage(run, instance);
    }

    /**
     * Get the CPU usage of the VCS for the given run.
     * @param run The run to get the CPU usage for.
     * @return A list of CPU usage values. An empty list if no Prometheus instance is configured for the VCS.
     */
    public List<MetricValue> getCpuUsageVcs(SimulationRun run) {
        log.info("Getting VCS CPU usage for {}", run);
        var instance = artemisConfiguration.getPrometheusInstanceVcs(run.getSimulation().getServer());
        if (instance == null || instance.isBlank()) {
            log.warn("No Prometheus instance configured for VCS on {}", run.getSimulation().getServer());
            return List.of();
        }
        return getCpuUsage(run, instance);
    }

    /**
     * Get the CPU usage of the CI system for the given run.
     * @param run The run to get the CPU usage for.
     * @return A list of CPU usage values. An empty list if no Prometheus instance is configured for the CI system.
     */
    public List<MetricValue> getCpuUsageCi(SimulationRun run) {
        log.info("Getting CI CPU usage for {}", run);
        var instance = artemisConfiguration.getPrometheusInstanceCi(run.getSimulation().getServer());
        if (instance == null || instance.isBlank()) {
            log.warn("No Prometheus instance configured for CI on {}", run.getSimulation().getServer());
            return List.of();
        }
        return getCpuUsage(run, instance);
    }

    /**
     * Execute a Prometheus query. The query is executed for the given time range.
     * @param query The query to execute.
     * @param start The start of the time range.
     * @param end The end of the time range.
     * @return The response from Prometheus.
     */
    public QueryResponse executeQuery(String query, ZonedDateTime start, ZonedDateTime end) {
        log.info("Querying Prometheus: {}", query);
        if (webClient == null) {
            setupWebclient();
        }
        return webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .path("/api/v1/query_range")
                    .query("query={query}&start={start}&end={end}&step={resolution}")
                    .build(query, asPrometheusTimestamp(start), asPrometheusTimestamp(end), resolution)
            )
            .retrieve()
            .bodyToMono(QueryResponse.class)
            .block();
    }

    private List<MetricValue> getCpuUsage(SimulationRun run, String instance) {
        // Prometheus query to get the idle-percentage of the given instance`s CPUs.
        // We use the idle-percentage because it is the inverse of the CPU usage.
        // We take the average across all CPUs.
        // The percentage is calculated over the last minute.
        var query = "avg(rate(node_cpu_seconds_total{instance=\"" + instance + "\", mode=\"idle\"}[1m]))";

        // If the run is still running, we want to get the CPU usage until now.
        // If the run is finished, we want to get the CPU usage until the end of the run plus 30 minutes.
        ZonedDateTime end = nowUTC();
        if (
            run.getStatus() == SimulationRun.Status.FINISHED ||
            run.getStatus() == SimulationRun.Status.FAILED ||
            run.getStatus() == SimulationRun.Status.CANCELLED
        ) {
            if (run.getEndDateTime() != null) {
                end = run.getEndDateTime().withZoneSameInstant(ZoneId.of("UTC")).plusMinutes(30);
            } else {
                // Edge case where the run is over but the end date is not set.
                end = run.getStartDateTime().withZoneSameInstant(ZoneId.of("UTC")).plusMinutes(30);
            }
            // We don't want to get CPU usage in the future, that results in weird graphs.
            if (end.isAfter(nowUTC())) {
                end = nowUTC();
            }
        }
        var res = executeQuery(query, run.getStartDateTime().withZoneSameInstant(ZoneId.of("UTC")).minusMinutes(30), end);
        List<MetricValue> values = new LinkedList<>();
        Arrays
            .stream(res.getData().getResult())
            .forEach(r ->
                Arrays
                    .stream(r.getValues())
                    .forEach(v -> {
                        // Prometheus returns a list of lists, where the first value is the timestamp and the second value is the cpu percentage in idle mode.
                        // We invert the value to get the cpu usage.
                        // The timestamp can be either a double or an int, depending on the given time range.
                        try {
                            values.add(new MetricValue((double) v[0], 1.0 - Double.parseDouble((String) v[1])));
                        } catch (ClassCastException e) {
                            values.add(new MetricValue((int) v[0], 1.0 - Double.parseDouble((String) v[1])));
                        }
                    })
            );
        return values;
    }

    private String asPrometheusTimestamp(ZonedDateTime dateTime) {
        Instant instant = dateTime.toInstant();
        double timestamp = instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
        return String.format(Locale.US, "%.3f", timestamp);
    }

    private ZonedDateTime nowUTC() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    private void setupWebclient() {
        this.webClient =
            WebClient
                .builder()
                .clientConnector(new ReactorClientHttpConnector())
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authToken)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
