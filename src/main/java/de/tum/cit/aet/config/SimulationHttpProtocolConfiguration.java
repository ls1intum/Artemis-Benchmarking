package de.tum.cit.aet.config;

import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Tells the simulated users which HTTP protocol to speak.
 * <p>
 * The users are constructed directly rather than by Spring, so the setting is handed to them once at startup instead
 * of being injected. It is a property of the run's transport, not of an individual user.
 */
@Configuration
public class SimulationHttpProtocolConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimulationHttpProtocolConfiguration.class);

    public SimulationHttpProtocolConfiguration(@Value("${benchmarking.simulation.http-protocol:auto}") String httpProtocol) {
        SimulatedArtemisUser.setHttpProtocol(httpProtocol);
        log.info("Simulated users will use HTTP protocol setting '{}'", httpProtocol);
    }
}
