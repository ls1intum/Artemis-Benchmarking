package de.tum.cit.aet.config;

import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Tells the simulated users how to talk to Artemis: which HTTP protocol, and how many connections the cohort may hold
 * open at once.
 * <p>
 * The users are constructed directly rather than by Spring, so the settings are handed to them once at startup instead
 * of being injected. They describe the run's transport, not an individual user.
 * <p>
 * {@code @Lazy(false)} is what makes that true, and is not optional. The application sets
 * {@code spring.main.lazy-initialization: true}, and this class declares no beans, so nothing ever asks for it: without
 * the annotation Spring never constructs it and both settings silently do nothing. That went unnoticed for the
 * protocol precisely because the value the property would have applied is also the one the field already held, so only
 * a run that tried to change it would have found out.
 */
@Configuration
@Lazy(false)
public class SimulationClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimulationClientConfiguration.class);

    public SimulationClientConfiguration(
        @Value("${benchmarking.simulation.http-protocol:auto}") String httpProtocol,
        @Value(
            "${benchmarking.simulation.connection-pool.max-connections:" + SimulatedArtemisUser.DEFAULT_MAX_CONNECTIONS + "}"
        ) int maxConnections
    ) {
        SimulatedArtemisUser.setHttpProtocol(httpProtocol);
        SimulatedArtemisUser.setMaxConnections(maxConnections);
        log.info("Simulated users will use HTTP protocol setting '{}' and at most {} connections", httpProtocol, maxConnections);
    }
}
