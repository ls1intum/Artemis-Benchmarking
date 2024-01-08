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

    @Value("${artemis.local.prometheus-instance}")
    private String localPrometheusInstance;

    @Value("${artemis.ts1.url}")
    private String test1Url;

    @Value("${artemis.ts1.cleanup-enabled}")
    private boolean test1Cleanup;

    @Value("${artemis.ts1.prometheus-instance}")
    private String test1PrometheusInstance;

    @Value("${artemis.ts3.url}")
    private String test3Url;

    @Value("${artemis.ts3.cleanup-enabled}")
    private boolean test3Cleanup;

    @Value("${artemis.ts3.prometheus-instance}")
    private String test3PrometheusInstance;

    @Value("${artemis.staging.url}")
    private String stagingUrl;

    @Value("${artemis.staging.cleanup-enabled}")
    private boolean stagingCleanup;

    @Value("${artemis.staging.prometheus-instance}")
    private String stagingPrometheusInstance;

    @Value("${artemis.staging2.url}")
    private String staging2Url;

    @Value("${artemis.staging2.cleanup-enabled}")
    private boolean staging2Cleanup;

    @Value("${artemis.staging2.prometheus-instance}")
    private String staging2PrometheusInstance;

    @Value("${artemis.production.url}")
    private String productionUrl;

    @Value("${artemis.production.cleanup-enabled}")
    private boolean productionCleanup;

    @Value("${artemis.production.prometheus-instance}")
    private String productionPrometheusInstance;

    public String getUrl(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localUrl;
            case TS1 -> test1Url;
            case TS3 -> test3Url;
            case STAGING -> stagingUrl;
            case STAGING2 -> staging2Url;
            case PRODUCTION -> productionUrl;
        };
    }

    public boolean getCleanup(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localCleanup;
            case TS1 -> test1Cleanup;
            case TS3 -> test3Cleanup;
            case STAGING -> stagingCleanup;
            case STAGING2 -> staging2Cleanup;
            case PRODUCTION -> productionCleanup;
        };
    }

    public String getPrometheusInstance(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localPrometheusInstance;
            case TS1 -> test1PrometheusInstance;
            case TS3 -> test3PrometheusInstance;
            case STAGING -> stagingPrometheusInstance;
            case STAGING2 -> staging2PrometheusInstance;
            case PRODUCTION -> productionPrometheusInstance;
        };
    }
}
