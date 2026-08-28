package de.tum.cit.aet.config;

import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Tells the simulated users which HTTP protocol to speak.
 * <p>
 * The users are constructed directly rather than by Spring, so the setting is handed to them once at startup instead
 * of being injected. It is a property of the run's transport, not of an individual user.
 * <p>
 * {@code @Lazy(false)} is what makes that true, and is not optional. The application sets
 * {@code spring.main.lazy-initialization: true}, and this class declares no beans, so nothing ever asks for it: without
 * the annotation Spring never constructs it, {@link SimulatedArtemisUser#setHttpProtocol} is never called, and
 * {@code benchmarking.simulation.http-protocol} silently does nothing. That went unnoticed precisely because the value
 * the property would have applied is also the one the field already holds, so only a run that tried to change the
 * protocol would have found out.
 */
@Configuration
@Lazy(false)
public class SimulationHttpProtocolConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimulationHttpProtocolConfiguration.class);

    public SimulationHttpProtocolConfiguration(@Value("${benchmarking.simulation.http-protocol:auto}") String httpProtocol) {
        SimulatedArtemisUser.setHttpProtocol(httpProtocol);
        log.info("Simulated users will use HTTP protocol setting '{}'", httpProtocol);
    }
}
