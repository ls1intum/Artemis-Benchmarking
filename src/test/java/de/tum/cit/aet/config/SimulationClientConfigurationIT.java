package de.tum.cit.aet.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.IntegrationTest;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The configured protocol has to reach the simulated users at startup.
 * <p>
 * It did not. The application runs with {@code spring.main.lazy-initialization: true} and
 * {@link SimulationClientConfiguration} declares no beans, so nothing ever asked for it and Spring never built
 * it — leaving {@code benchmarking.simulation.http-protocol} a setting that quietly did nothing. Nobody noticed,
 * because the value the property would have applied is also the one the field already held; only a run that tried to
 * change the protocol would have discovered it.
 * <p>
 * This is an integration test rather than a unit test on purpose: the thing that broke was the wiring, not the code.
 */
@IntegrationTest
@TestPropertySource(
    properties = { "benchmarking.simulation.http-protocol=h1", "benchmarking.simulation.connection-pool.max-connections=4321" }
)
class SimulationClientConfigurationIT {

    @AfterAll
    static void restoreTheDefaults() {
        // The settings live in static fields, so a test class that changes them has to put them back.
        //
        // After all of them, not after each: the configuration that applies them runs once, when the context starts.
        // Resetting between tests would leave every test after the first asserting against the defaults it just
        // restored, which is exactly how this was written first and exactly how it failed.
        SimulatedArtemisUser.setHttpProtocol("auto");
        SimulatedArtemisUser.setMaxConnections(SimulatedArtemisUser.DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    void theConfiguredProtocolReachesTheSimulatedUsersEvenThoughNothingDependsOnIt() {
        assertThat(SimulatedArtemisUser.httpProtocol()).isEqualTo("h1");
    }

    @Test
    void theConnectionCeilingReachesThemToo() {
        // Left at reactor-netty's default this is max(cores, 8) * 2 — sixteen — shared by the whole cohort, which
        // caps a run well below anything a real one would produce.
        assertThat(SimulatedArtemisUser.maxConnections()).isEqualTo(4321);
    }
}
