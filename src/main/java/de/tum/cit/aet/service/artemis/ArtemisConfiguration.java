package de.tum.cit.aet.service.artemis;

import de.tum.cit.aet.util.ArtemisServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtemisConfiguration {

    @Value("${artemis.local.url}")
    private String localUrl;

    @Value("${artemis.local.cleanup-enabled}")
    private boolean localCleanup;

    @Value("${artemis.local.prometheus-instances.artemis}")
    private String[] localPrometheusInstanceArtemis;

    @Value("${artemis.local.prometheus-instances.vcs}")
    private String[] localPrometheusInstanceVcs;

    @Value("${artemis.local.prometheus-instances.ci}")
    private String[] localPrometheusInstanceCi;

    @Value("${artemis.local.is-local}")
    private boolean localIsLocal;

    @Value("${artemis.ts1.url}")
    private String test1Url;

    @Value("${artemis.ts1.cleanup-enabled}")
    private boolean test1Cleanup;

    @Value("${artemis.ts1.prometheus-instances.artemis}")
    private String[] test1PrometheusInstanceArtemis;

    @Value("${artemis.ts1.prometheus-instances.vcs}")
    private String[] test1PrometheusInstanceVcs;

    @Value("${artemis.ts1.prometheus-instances.ci}")
    private String[] test1PrometheusInstanceCi;

    @Value("${artemis.ts1.is-local}")
    private boolean test1IsLocal;

    @Value("${artemis.ts3.url}")
    private String test3Url;

    @Value("${artemis.ts3.cleanup-enabled}")
    private boolean test3Cleanup;

    @Value("${artemis.ts3.prometheus-instances.artemis}")
    private String[] test3PrometheusInstanceArtemis;

    @Value("${artemis.ts3.prometheus-instances.vcs}")
    private String[] test3PrometheusInstanceVcs;

    @Value("${artemis.ts3.prometheus-instances.ci}")
    private String[] test3PrometheusInstanceCi;

    @Value("${artemis.ts3.is-local}")
    private boolean test3IsLocal;

    @Value("${artemis.ts7.url}")
    private String test7Url;

    @Value("${artemis.ts7.cleanup-enabled}")
    private boolean test7Cleanup;

    @Value("${artemis.ts7.prometheus-instances.artemis}")
    private String[] test7PrometheusInstanceArtemis;

    @Value("${artemis.ts7.prometheus-instances.vcs}")
    private String[] test7PrometheusInstanceVcs;

    @Value("${artemis.ts7.prometheus-instances.ci}")
    private String[] test7PrometheusInstanceCi;

    @Value("${artemis.ts7.is-local}")
    private boolean test7IsLocal;

    @Value("${artemis.ts8.url}")
    private String test8Url;

    @Value("${artemis.ts8.cleanup-enabled}")
    private boolean test8Cleanup;

    @Value("${artemis.ts8.prometheus-instances.artemis}")
    private String[] test8PrometheusInstanceArtemis;

    @Value("${artemis.ts8.prometheus-instances.vcs}")
    private String[] test8PrometheusInstanceVcs;

    @Value("${artemis.ts8.prometheus-instances.ci}")
    private String[] test8PrometheusInstanceCi;

    @Value("${artemis.ts8.is-local}")
    private boolean test8IsLocal;

    @Value("${artemis.staging.url}")
    private String stagingUrl;

    @Value("${artemis.staging.cleanup-enabled}")
    private boolean stagingCleanup;

    @Value("${artemis.staging.prometheus-instances.artemis}")
    private String[] stagingPrometheusInstanceArtemis;

    @Value("${artemis.staging.prometheus-instances.vcs}")
    private String[] stagingPrometheusInstanceVcs;

    @Value("${artemis.staging.prometheus-instances.ci}")
    private String[] stagingPrometheusInstanceCi;

    @Value("${artemis.staging.is-local}")
    private boolean stagingIsLocal;

    @Value("${artemis.staging2.url}")
    private String staging2Url;

    @Value("${artemis.staging2.cleanup-enabled}")
    private boolean staging2Cleanup;

    @Value("${artemis.staging2.prometheus-instances.artemis}")
    private String[] staging2PrometheusInstanceArtemis;

    @Value("${artemis.staging2.prometheus-instances.vcs}")
    private String[] staging2PrometheusInstanceVcs;

    @Value("${artemis.staging2.prometheus-instances.ci}")
    private String[] staging2PrometheusInstanceCi;

    @Value("${artemis.staging2.is-local}")
    private boolean staging2IsLocal;

    @Value("${artemis.devcluster.url}")
    private String devclusterUrl;

    @Value("${artemis.devcluster.cleanup-enabled}")
    private boolean devclusterCleanup;

    @Value("${artemis.devcluster.prometheus-instances.artemis}")
    private String[] devclusterPrometheusInstanceArtemis;

    @Value("${artemis.devcluster.prometheus-instances.vcs}")
    private String[] devclusterPrometheusInstanceVcs;

    @Value("${artemis.devcluster.prometheus-instances.ci}")
    private String[] devclusterPrometheusInstanceCi;

    @Value("${artemis.devcluster.is-local}")
    private boolean devclusterIsLocal;

    @Value("${artemis.production.url}")
    private String productionUrl;

    @Value("${artemis.production.cleanup-enabled}")
    private boolean productionCleanup;

    @Value("${artemis.production.prometheus-instances.artemis}")
    private String[] productionPrometheusInstanceArtemis;

    @Value("${artemis.production.prometheus-instances.vcs}")
    private String[] productionPrometheusInstanceVcs;

    @Value("${artemis.production.prometheus-instances.ci}")
    private String[] productionPrometheusInstanceCi;

    @Value("${artemis.production.is-local}")
    private boolean productionIsLocal;

    /**
     * Get the URL of the Artemis server.
     *
     * @param server the Artemis server.
     * @return the URL of the Artemis server.
     */
    public String getUrl(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localUrl;
            case TS1 -> test1Url;
            case TS3 -> test3Url;
            case TS7 -> test7Url;
            case TS8 -> test8Url;
            case STAGING -> stagingUrl;
            case STAGING2 -> staging2Url;
            case DEVCLUSTER -> devclusterUrl;
            case PRODUCTION -> productionUrl;
        };
    }

    /**
     * Get the cleanup flag of the Artemis server.
     *
     * @param server the Artemis server.
     * @return the cleanup flag of the Artemis server.
     */
    public boolean getCleanup(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localCleanup;
            case TS1 -> test1Cleanup;
            case TS3 -> test3Cleanup;
            case TS7 -> test7Cleanup;
            case TS8 -> test8Cleanup;
            case STAGING -> stagingCleanup;
            case STAGING2 -> staging2Cleanup;
            case DEVCLUSTER -> devclusterCleanup;
            case PRODUCTION -> productionCleanup;
        };
    }

    /**
     * Get the Prometheus instances of the Artemis server.
     *
     * @param server the Artemis server.
     * @return the Prometheus instances of the Artemis server.
     */
    public String[] getPrometheusInstancesArtemis(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localPrometheusInstanceArtemis;
            case TS1 -> test1PrometheusInstanceArtemis;
            case TS3 -> test3PrometheusInstanceArtemis;
            case TS7 -> test7PrometheusInstanceArtemis;
            case TS8 -> test8PrometheusInstanceArtemis;
            case STAGING -> stagingPrometheusInstanceArtemis;
            case STAGING2 -> staging2PrometheusInstanceArtemis;
            case DEVCLUSTER -> devclusterPrometheusInstanceArtemis;
            case PRODUCTION -> productionPrometheusInstanceArtemis;
        };
    }

    /**
     * Get the Prometheus instances of the VCS of the Artemis server.
     *
     * @param server the Artemis server.
     * @return the Prometheus instances of the VCS of the Artemis server.
     */
    public String[] getPrometheusInstancesVcs(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localPrometheusInstanceVcs;
            case TS1 -> test1PrometheusInstanceVcs;
            case TS3 -> test3PrometheusInstanceVcs;
            case TS7 -> test7PrometheusInstanceVcs;
            case TS8 -> test8PrometheusInstanceVcs;
            case STAGING -> stagingPrometheusInstanceVcs;
            case STAGING2 -> staging2PrometheusInstanceVcs;
            case DEVCLUSTER -> devclusterPrometheusInstanceVcs;
            case PRODUCTION -> productionPrometheusInstanceVcs;
        };
    }

    /**
     * Get the Prometheus instances of the CI of the Artemis server.
     *
     * @param server the Artemis server.
     * @return the Prometheus instances of the CI of the Artemis server.
     */
    public String[] getPrometheusInstancesCi(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localPrometheusInstanceCi;
            case TS1 -> test1PrometheusInstanceCi;
            case TS3 -> test3PrometheusInstanceCi;
            case TS7 -> test7PrometheusInstanceCi;
            case TS8 -> test8PrometheusInstanceCi;
            case STAGING -> stagingPrometheusInstanceCi;
            case STAGING2 -> staging2PrometheusInstanceCi;
            case DEVCLUSTER -> devclusterPrometheusInstanceCi;
            case PRODUCTION -> productionPrometheusInstanceCi;
        };
    }

    /**
     * Get the is-local flag of the Artemis server.
     *
     * @param server the Artemis server.
     * @return the is-local flag of the Artemis server.
     */
    public boolean getIsLocal(ArtemisServer server) {
        return switch (server) {
            case LOCAL -> localIsLocal;
            case TS1 -> test1IsLocal;
            case TS3 -> test3IsLocal;
            case TS7 -> test7IsLocal;
            case TS8 -> test8IsLocal;
            case STAGING -> stagingIsLocal;
            case STAGING2 -> staging2IsLocal;
            case DEVCLUSTER -> devclusterIsLocal;
            case PRODUCTION -> productionIsLocal;
        };
    }
}
