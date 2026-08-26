package de.tum.cit.aet.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the clock the application reads the current time from.
 * <p>
 * Time-dependent behaviour that reaches for {@code ZonedDateTime.now()} directly cannot be tested at a chosen instant,
 * only at whatever instant the test happens to run. The scheduling tests learned this the hard way: they expressed
 * "later today" as an offset from the real clock, which stops being later today once the offset crosses midnight, and
 * four of them failed every night between 23:00 and 01:00 UTC.
 */
@Configuration
public class ClockConfiguration {

    /**
     * The system clock in UTC, which is the zone schedules are reasoned about in.
     *
     * @return the clock the application uses
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
