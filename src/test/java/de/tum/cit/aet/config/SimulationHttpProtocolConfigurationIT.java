package de.tum.cit.aet.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.IntegrationTest;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The configured protocol has to reach the simulated users at startup.
 * <p>
 * It did not. The application runs with {@code spring.main.lazy-initialization: true} and
 * {@link SimulationHttpProtocolConfiguration} declares no beans, so nothing ever asked for it and Spring never built
 * it — leaving {@code benchmarking.simulation.http-protocol} a setting that quietly did nothing. Nobody noticed,
 * because the value the property would have applied is also the one the field already held; only a run that tried to
 * change the protocol would have discovered it.
 * <p>
 * This is an integration test rather than a unit test on purpose: the thing that broke was the wiring, not the code.
 */
@IntegrationTest
@TestPropertySource(properties = "benchmarking.simulation.http-protocol=h1")
class SimulationHttpProtocolConfigurationIT {

    @AfterEach
    void restoreTheDefault() {
        // The setting lives in a static field, so a test that changes it has to put it back.
        SimulatedArtemisUser.setHttpProtocol("auto");
    }

    @Test
    void theConfiguredProtocolReachesTheSimulatedUsersEvenThoughNothingDependsOnIt() {
        assertThat(SimulatedArtemisUser.httpProtocol()).isEqualTo("h1");
    }
}
