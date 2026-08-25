package de.tum.cit.aet.service.artemis.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.artemisModel.ArtemisAuthMechanism;
import de.tum.cit.aet.domain.RequestStat;
import de.tum.cit.aet.domain.RequestType;
import de.tum.cit.aet.service.artemis.interaction.browser.BrowserSimulationSettings;
import de.tum.cit.aet.service.artemis.interaction.browser.StaticAssetCatalog;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Covers the behaviour that makes a simulated student resemble a browser: downloading the client bundle and writing the
 * open submission back repeatedly rather than once.
 */
class SimulatedArtemisStudentTest {

    private static final String AUTH_COOKIE = "jwt=token; Path=/; Max-Age=3600; Expires=Tue, 01 Jan 2030 00:00:00 GMT";

    private final Queue<String> requested = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void reset() {
        StaticAssetCatalog.clear();
        requested.clear();
    }

    @Test
    void initialCallsDownloadTheClientBundle() {
        SimulatedArtemisStudent student = student(BrowserSimulationSettings.defaults());
        student.login();
        requested.clear();

        List<RequestStat> stats = student.performInitialCalls();

        long staticRequests = stats
            .stream()
            .filter(stat -> stat.type() == RequestType.STATIC_RESOURCE)
            .count();
        assertTrue(staticRequests >= 3, "expected index.html, the stylesheet and the entry bundle, got " + staticRequests);
        assertTrue(requested.contains("/"), "the student must ask for index.html like a browser does");
        assertTrue(requested.contains("/main-AAAAAAAA.js"), "the student must download the entry bundle");
        assertTrue(requested.contains("/styles-BBBBBBBB.css"), "the student must download the stylesheet");
    }

    @Test
    void initialCallsSkipTheBundleWhenStaticResourcesAreOff() {
        SimulatedArtemisStudent student = student(BrowserSimulationSettings.withoutStaticResources());
        student.login();
        requested.clear();

        List<RequestStat> stats = student.performInitialCalls();

        assertEquals(
            0,
            stats
                .stream()
                .filter(stat -> stat.type() == RequestType.STATIC_RESOURCE)
                .count()
        );
        assertTrue(requested.stream().noneMatch(path -> path.endsWith(".js")));
    }

    @Test
    void openingAViewAsksTheServerForTheTimeSeveralTimes() {
        // Several client components ask on initialisation, so a view costs a burst rather than a single call.
        SimulatedArtemisStudent student = student(BrowserSimulationSettings.defaults());
        student.login();
        requested.clear();

        student.performInitialCalls();

        long timeCalls = requested.stream().filter("/api/public/time"::equals).count();
        assertTrue(
            timeCalls >= BrowserSimulationSettings.DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION,
            "expected a burst of at least " +
                BrowserSimulationSettings.DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION +
                " clock calls, got " +
                timeCalls
        );
    }

    @Test
    void aViewCostsNoClockCallsWhenTheBurstIsSwitchedOff() {
        BrowserSimulationSettings noBurst = new BrowserSimulationSettings(false, 100, 2000, 6, 4, 39, 0);
        SimulatedArtemisStudent student = student(noBurst);
        student.login();
        requested.clear();

        student.performInitialCalls();

        // Only the one explicit call performInitialCalls has always made.
        assertEquals(1, requested.stream().filter("/api/public/time"::equals).count());
    }

    private SimulatedArtemisStudent student(BrowserSimulationSettings settings) {
        return new SimulatedArtemisStudent(
            "http://localhost",
            "student",
            "password",
            ArtemisAuthMechanism.ONLINE_IDE,
            settings,
            this::webClientBuilder
        );
    }

    private WebClient.Builder webClientBuilder() {
        return WebClient.builder().clientConnector(new HttpHandlerConnector(RouterFunctions.toHttpHandler(router())));
    }

    private RouterFunction<ServerResponse> router() {
        AtomicInteger ignored = new AtomicInteger();
        return RouterFunctions.route()
            .GET("/", request ->
                record(
                    "/",
                    ok(
                        MediaType.TEXT_HTML,
                        "<link rel=\"stylesheet\" href=\"styles-BBBBBBBB.css\"><script src=\"main-AAAAAAAA.js\"></script>"
                    )
                )
            )
            .GET("/main-AAAAAAAA.js", request ->
                record("/main-AAAAAAAA.js", ok(MediaType.valueOf("text/javascript"), "import\"./chunk-CCCCCCCC.js\";"))
            )
            .GET("/styles-BBBBBBBB.css", request -> record("/styles-BBBBBBBB.css", ok(MediaType.valueOf("text/css"), "body{color:red}")))
            .GET("/chunk-CCCCCCCC.js", request ->
                record("/chunk-CCCCCCCC.js", ok(MediaType.valueOf("text/javascript"), "export const x=" + ignored.incrementAndGet() + ";"))
            )
            .GET("/management/info", request -> record("/management/info", ok(MediaType.APPLICATION_JSON, "{\"activeProfiles\":[]}")))
            .GET("/api/public/time", request -> record("/api/public/time", ok(MediaType.APPLICATION_JSON, "\"2026-08-25T10:00:00Z\"")))
            .GET("/api/core/public/account", request ->
                record(
                    "/api/core/public/account",
                    ok(MediaType.APPLICATION_JSON, "{\"login\":\"student\",\"authorities\":[\"ROLE_USER\"]}")
                )
            )
            .GET("/api/programming/ssh-settings/public-keys", request ->
                record("/api/programming/ssh-settings/public-keys", ok(MediaType.APPLICATION_JSON, "[]"))
            )
            .GET("/api/**", request -> record(request.path(), ok(MediaType.APPLICATION_JSON, "{}")))
            .POST("/api/core/public/authenticate", request ->
                record("/api/core/public/authenticate", ServerResponse.ok().header("Set-Cookie", AUTH_COOKIE).build())
            )
            .POST("/api/**", request -> record(request.path(), ok(MediaType.APPLICATION_JSON, "{}")))
            .build();
    }

    private Mono<ServerResponse> ok(MediaType type, String body) {
        return ServerResponse.ok().contentType(type).bodyValue(body);
    }

    private Mono<ServerResponse> record(String path, Mono<ServerResponse> response) {
        requested.add(path);
        return response;
    }
}
