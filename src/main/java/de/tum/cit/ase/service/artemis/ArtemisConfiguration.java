package de.tum.cit.ase.service.artemis;

import de.tum.cit.ase.util.ArtemisServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtemisConfiguration {

    @Value("${artemis.local.url}")
    private String localUrl;

    @Value("${artemis.local.cleanup-enabled}")
    private boolean localCleanup;

    @Value("${artemis.ts1.url}")
    private String test1Url;

    @Value("${artemis.ts1.cleanup-enabled}")
    private boolean test1Cleanup;

    @Value("${artemis.ts3.url}")
    private String test3Url;

    @Value("${artemis.ts3.cleanup-enabled}")
    private boolean test3Cleanup;

    @Value("${artemis.staging.url}")
    private String stagingUrl;

    @Value("${artemis.staging.cleanup-enabled}")
    private boolean stagingCleanup;

    @Value("${artemis.production.url}")
    private String productionUrl;

    @Value("${artemis.production.cleanup-enabled}")
    private boolean productionCleanup;

    public String getUrl(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localUrl;
            case TS1 -> test1Url;
            case TS3 -> test3Url;
            case STAGING -> stagingUrl;
            case PRODUCTION -> productionUrl;
        };
    }

    public boolean getCleanup(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localCleanup;
            case TS1 -> test1Cleanup;
            case TS3 -> test3Cleanup;
            case STAGING -> stagingCleanup;
            case PRODUCTION -> productionCleanup;
        };
    }
}
