package de.tum.cit.aet.service.artemis.interaction;

import static de.tum.cit.aet.domain.RequestType.*;
import static de.tum.cit.aet.util.TimeLogUtil.formatDurationFrom;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import com.thedeanda.lorem.LoremIpsum;
import de.tum.cit.aet.artemisModel.*;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.domain.OnlineIdeFileSubmission;
import de.tum.cit.aet.domain.RequestStat;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.artemis.interaction.browser.BrowserSimulationSettings;
import de.tum.cit.aet.service.artemis.interaction.browser.StaticAssetCatalog;
import de.tum.cit.aet.service.artemis.interaction.browser.StaticResourceFetcher;
import de.tum.cit.aet.service.artemis.util.ArtemisServerInfo;
import de.tum.cit.aet.service.artemis.util.CourseAvailableTabsDTO;
import de.tum.cit.aet.service.artemis.util.CourseExercisesForOverviewDTO;
import de.tum.cit.aet.service.artemis.util.CourseForOverviewDTO;
import de.tum.cit.aet.service.artemis.util.ScienceEventDTO;
import de.tum.cit.aet.service.artemis.util.UserSshPublicKeyDTO;
import de.tum.cit.aet.util.FileGeneratorUtil;
import de.tum.cit.aet.util.SimulationConcurrency;
import de.tum.cit.aet.util.UMLClassDiagrams;
import jakarta.annotation.Nullable;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A simulated Artemis student that can be used to interact with the Artemis server.
 */
public class SimulatedArtemisStudent extends SimulatedArtemisUser {

    private static final int MAX_RETRIES = 4; // Maximum number of retries for clone
    private static final long RETRY_DELAY_MS = 5000; // Delay between clone retries in milliseconds

    private String courseIdString;
    private String examIdString;
    private Long studentExamId;
    private StudentExam studentExam;
    private String participationVcsAccessToken;
    private Long latestResultId;
    private SimulatedArtemisWebsocket websocket;
    private final ArtemisAuthMechanism authenticationMechanism;

    private final int numberOfCommitsAndPushesFrom;
    private final int numberOfCommitsAndPushesTo;

    private boolean isScienceFeatureEnabled = false;
    private boolean isIrisEnabled = false;

    private final BrowserSimulationSettings browserSettings;

    /**
     * Downloads the client bundle for this student. Created on the first call rather than in the constructor because it
     * needs the authenticated {@link #webClient}, which only exists after login.
     */
    private StaticResourceFetcher staticResources;

    public SimulatedArtemisStudent(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        int numberOfCommitsAndPushesFrom,
        int numberOfCommitsAndPushesTo,
        ArtemisAuthMechanism authMechanism
    ) {
        this(
            artemisUrl,
            artemisUser,
            artemisUserService,
            numberOfCommitsAndPushesFrom,
            numberOfCommitsAndPushesTo,
            authMechanism,
            BrowserSimulationSettings.defaults()
        );
    }

    public SimulatedArtemisStudent(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        int numberOfCommitsAndPushesFrom,
        int numberOfCommitsAndPushesTo,
        ArtemisAuthMechanism authMechanism,
        BrowserSimulationSettings browserSettings
    ) {
        super(artemisUrl, artemisUser, artemisUserService);
        this.browserSettings = browserSettings;
        log = LoggerFactory.getLogger(SimulatedArtemisStudent.class.getName() + "." + username);
        this.numberOfCommitsAndPushesFrom = numberOfCommitsAndPushesFrom;
        this.numberOfCommitsAndPushesTo = numberOfCommitsAndPushesTo;
        this.authenticationMechanism = authMechanism;
        // for old users in the DB which might never gotten a key pair generated
        if (artemisUser.getPublicKey() == null || artemisUser.getPrivateKey() == null) {
            var savedUser = artemisUserService.generateKeyPair(artemisUser);
            this.publicKeyString = savedUser.getPublicKey();
            this.privateKeyString = savedUser.getPrivateKey();
        } else {
            this.publicKeyString = artemisUser.getPublicKey();
            this.privateKeyString = artemisUser.getPrivateKey();
        }
    }

    /**
     * Creates a student that authenticates with the given credentials and talks through the given client builder,
     * without the database-backed token caching the production constructors use.
     * <p>
     * Exists so tests can point a student at an in-memory server; the production code always has an
     * {@link ArtemisUser} to cache the token in.
     *
     * @param artemisUrl               the URL of the Artemis server
     * @param username                 the username to log in with
     * @param password                 the password to log in with
     * @param authMechanism            the authentication mechanism the student uses for git
     * @param browserSettings          how much of a real browser's behaviour to reproduce
     * @param webClientBuilderSupplier supplies the builder every request goes through
     */
    SimulatedArtemisStudent(
        String artemisUrl,
        String username,
        String password,
        ArtemisAuthMechanism authMechanism,
        BrowserSimulationSettings browserSettings,
        Supplier<WebClient.Builder> webClientBuilderSupplier
    ) {
        super(artemisUrl, username, password, webClientBuilderSupplier);
        log = LoggerFactory.getLogger(SimulatedArtemisStudent.class.getName() + "." + username);
        this.numberOfCommitsAndPushesFrom = 1;
        this.numberOfCommitsAndPushesTo = 2;
        this.authenticationMechanism = authMechanism;
        this.browserSettings = browserSettings;
    }

    /**
     * Records an action and then pauses, so the user's next request does not start the instant this one returned.
     * <p>
     * Wrapping the result rather than the call keeps the pause after the action, which is where a real user's reading
     * or typing happens. Outside a simulation the pause does nothing.
     *
     * @param stat the statistic of the action that just completed
     * @return the same statistic
     */
    private RequestStat paced(RequestStat stat) {
        SimulationConcurrency.currentThinkTime().pause();
        return stat;
    }

    @Override
    protected void checkAccess() {
        var response = webClient.get().uri("api/core/public/account").retrieve().bodyToMono(User.class).block();
        this.authenticated = response != null && response.getAuthorities().contains("ROLE_USER");
    }

    /**
     * Perform miscellaneous calls to Artemis, e.g. to get the user info, system notifications, account, notification settings, and courses.
     *
     * @return the list of request stats
     */
    public List<RequestStat> performInitialCalls() {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }

        List<RequestStat> requestStats = new ArrayList<>(loadAppShell());
        // Landing on the course dashboard after signing in is the first in-app navigation.
        requestStats.addAll(loadRouteChunks());
        requestStats.addAll(
            List.of(
                paced(getInfo()),
                paced(getServerTime()),
                paced(getSystemNotifications()),
                paced(getAccount()),
                paced(getGlobalNotificationSettings()),
                paced(getCourses()),
                paced(getCalendarSubscriptionToken()),
                paced(configureSSH())
            )
        );
        return requestStats;
    }

    /**
     * Downloads the Artemis client the way the student's browser does when they open the site.
     * <p>
     * Without this a simulated student produces the REST traffic of an exam but none of its bytes: a traced session
     * spent 547 of its 632 requests and 20 of its 20.5 MB on the client bundle. Skipped when the run has static
     * resources switched off, and cheap for a student whose browser cache is warm.
     *
     * @return one stat per downloaded file
     */
    private List<RequestStat> loadAppShell() {
        if (!browserSettings.staticResourcesEnabled()) {
            return List.of();
        }
        StaticAssetCatalog catalog = StaticAssetCatalog.forServer(artemisUrl, webClient, browserSettings.maxAssets());
        if (catalog.isEmpty()) {
            return List.of();
        }
        staticResources = new StaticResourceFetcher(webClient, catalog, browserSettings);
        log.debug("Browser cache for {} is {}", username, staticResources.isColdCache() ? "cold" : "warm");
        return staticResources.loadAppShell();
    }

    /**
     * Downloads the chunks a navigation pulls in, the way the browser's module loader does when the student opens a
     * new view.
     *
     * @return one stat per downloaded file, empty when static resources are off or the bundle is already downloaded
     */
    private List<RequestStat> loadRouteChunks() {
        if (staticResources == null) {
            return List.of();
        }
        return staticResources.loadRouteChunks();
    }

    /**
     * Participate in an exam, i.e. solve and submit the exercises and fetch live events.
     *
     * @param courseId the ID of the course
     * @param examId   the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> participateInExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.addAll(ensureStudentExamLoaded());
        if (studentExam == null) {
            log.warn("Skipping exam participation for {}: student exam not available.", username);
            return requestStats;
        }
        requestStats.add(paced(getServerTime()));
        requestStats.add(paced(fetchLiveEvents()));
        requestStats.addAll(handleExercises());

        return requestStats;
    }

    /**
     * Start participating in an exam, i.e. navigate into the exam and start the exam.
     *
     * @param courseId the ID of the course
     * @param examId   the ID of the exam
     * @param courseProgrammingExerciseId the ID of the course programming exercise
     * @return the list of request stats
     */
    public List<RequestStat> startExamParticipation(long courseId, long examId, long courseProgrammingExerciseId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.addAll(loadRouteChunks());
        requestStats.add(paced(getCourseOverview(courseProgrammingExerciseId)));
        requestStats.add(paced(getServerTime()));
        requestStats.add(paced(getCoursesDropdown()));
        requestStats.add(paced(getScienceSettings()));
        requestStats.add(paced(getNotificationSettings()));
        requestStats.add(paced(getNotificationInfo()));
        if (courseProgrammingExerciseId > 0) {
            if (isScienceFeatureEnabled) {
                requestStats.add(paced(putScienceEvent(courseProgrammingExerciseId)));
            }
            requestStats.add(paced(getExerciseDetails(courseProgrammingExerciseId)));
            requestStats.add(paced(getExerciseContributions(courseProgrammingExerciseId)));
        }
        if (isIrisEnabled) {
            requestStats.addAll(List.of(getIrisStatus(courseId), getIrisChatHistory(courseId)));
        }
        requestStats.addAll(loadRouteChunks());
        requestStats.add(paced(navigateIntoExam()));
        requestStats.add(paced(getTestExams()));
        requestStats.add(paced(getExamSideBarData()));
        requestStats.add(paced(startExam()));

        if (studentExam == null) {
            log.warn("Student exam not available after start for {}", username);
        }

        // Mirror the real client: open the exam websocket and subscribe to the exam live-event topics.
        // The connection is kept open across the participation phases and closed in submitAndEndExam.
        requestStats.add(paced(connectAndSubscribeExamWebsocket(examId)));

        return requestStats;
    }

    /**
     * Submit and end an exam, i.e. submit the student exam and load the exam summary.
     *
     * @param courseId the ID of the course
     * @param examId   the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> submitAndEndExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.addAll(ensureStudentExamLoaded());
        if (studentExam == null) {
            log.warn("Skipping exam submission for {}: student exam not available.", username);
            disconnectWebsocket();
            return requestStats;
        }
        requestStats.add(paced(getServerTime()));
        RequestStat submitStat = submitStudentExam();
        if (submitStat != null) {
            requestStats.add(submitStat);
        }
        requestStats.addAll(loadRouteChunks());
        requestStats.add(paced(loadExamSummary()));

        // End of the exam session: close the websocket the same way the real client does on hand-in.
        disconnectWebsocket();

        return requestStats;
    }

    private RequestStat getInfo() {
        long start = System.nanoTime();
        ArtemisServerInfo response = webClient.get().uri("management/info").retrieve().bodyToMono(ArtemisServerInfo.class).block();
        if (response != null) {
            // An Artemis version that omits either list must not take every student down with a NullPointerException.
            isScienceFeatureEnabled = response.features() != null && response.features().contains("Science");
            isIrisEnabled = response.activeProfiles() != null && response.activeProfiles().contains("iris");
        }
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getSystemNotifications() {
        long start = System.nanoTime();
        webClient.get().uri("api/notification/public/system-notifications/active").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getAccount() {
        long start = System.nanoTime();
        webClient.get().uri("api/core/public/account").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getGlobalNotificationSettings() {
        long start = System.nanoTime();
        webClient.get().uri("api/notification/global-notification-settings").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat configureSSH() {
        long start = System.nanoTime();
        List<UserSshPublicKeyDTO> keys = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("api/programming/ssh-settings/public-keys").build())
            .retrieve()
            .bodyToFlux(UserSshPublicKeyDTO.class)
            .collectList()
            .block();

        var hasArtemisKeyStoredAlready = keys.stream().anyMatch(key -> key.publicKey().equals(publicKeyString));

        if (!hasArtemisKeyStoredAlready) {
            try {
                webClient
                    .post()
                    .uri("api/programming/ssh-settings/public-key")
                    .bodyValue(UserSshPublicKeyDTO.of(publicKeyString))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            } catch (Exception e) {
                log.error("Error while adding SSH key for {{}}: {{}}", username, e.getMessage());
            }
        }

        return new RequestStat(now(), System.nanoTime() - start, SETUP_SSH_KEYS);
    }

    private RequestStat getCourses() {
        long start = System.nanoTime();
        webClient.get().uri("api/course/courses/for-dashboard").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    /**
     * Fetch the current server time. Real clients poll this endpoint periodically to keep the exam countdown in
     * sync; it is therefore one of the hottest endpoints during an exam and tracked under its own request type.
     *
     * @return the request stat
     */
    private RequestStat getServerTime() {
        long start = System.nanoTime();
        try {
            webClient.get().uri("api/public/time").retrieve().toBodilessEntity().block();
        } catch (Exception e) {
            log.debug("Could not fetch server time for {}: {}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, SERVER_TIME);
    }

    private RequestStat getCalendarSubscriptionToken() {
        long start = System.nanoTime();
        try {
            // This endpoint returns a raw token as text/plain, so we must not request application/json (-> 406).
            webClient
                .get()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "calendar", "subscription-token").build())
                .accept(MediaType.TEXT_PLAIN)
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.debug("Could not fetch calendar subscription token for {}: {}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getExerciseContributions(long exerciseId) {
        long start = System.nanoTime();
        try {
            webClient
                .get()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "atlas", "exercises", String.valueOf(exerciseId), "contributions").build())
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.debug("Could not fetch exercise contributions for {}: {}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    /**
     * Open the exam websocket and subscribe to the exam live-event topics, mirroring the real exam client.
     * Best-effort: a websocket failure is logged and never aborts the participation.
     *
     * @param examId the ID of the exam
     * @return the request stat for the websocket connect
     */
    private RequestStat connectAndSubscribeExamWebsocket(long examId) {
        long start = System.nanoTime();
        try {
            this.websocket = new SimulatedArtemisWebsocket(artemisUrl, authToken != null ? authToken.jwtToken() : null);
            if (websocket.connect()) {
                if (studentExamId != null) {
                    websocket.subscribe("/topic/exam-participation/studentExam/" + studentExamId + "/events");
                }
                websocket.subscribe("/topic/exam-participation/exam/" + examId + "/events");
            } else {
                log.warn("Websocket did not connect for {}", username);
            }
        } catch (Exception e) {
            log.warn("Could not establish exam websocket for {}: {}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, WEBSOCKET);
    }

    /**
     * Subscribe to the personal programming submission/result topics over the open exam websocket, mirroring the
     * real programming-exercise client (build results are pushed, not polled).
     */
    private void subscribeProgrammingWebsocketTopics() {
        if (websocket == null || !websocket.isConnected()) {
            return;
        }
        websocket.subscribe("/user/topic/newSubmissions");
        websocket.subscribe("/user/topic/submissionProcessing");
        websocket.subscribe("/user/topic/newResults");
    }

    private void disconnectWebsocket() {
        if (websocket != null) {
            websocket.disconnect();
            websocket = null;
        }
    }

    /**
     * Simulate a student entering the course overview.
     *
     * <p>Mirrors what the Artemis web client does since PR #12999 ("Load course overview content per tab instead of all
     * at once"): the shell, the available tabs and the exercises tab are fetched separately, rather than pulling the
     * whole course from {@code courses/&#123;courseId&#125;/for-dashboard}. That endpoint is deprecated and is kept only
     * for the native clients, and it costs 16 database queries and ~37 KB against ~6 queries for this split, so a
     * simulation that still called it would be measuring a load pattern no web user produces.
     *
     * @param exerciseId the programming exercise whose channel the student would open
     * @return the request stat covering the whole course-entry interaction
     */
    private RequestStat getCourseOverview(long exerciseId) {
        long start = System.nanoTime();

        CourseAvailableTabsDTO availableTabs = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "course", "courses", courseIdString, "available-tabs").build())
            .retrieve()
            .bodyToMono(CourseAvailableTabsDTO.class)
            .block();

        CourseForOverviewDTO course = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "course", "courses", courseIdString, "for-overview").build())
            .retrieve()
            .bodyToMono(CourseForOverviewDTO.class)
            .block();

        // The student lands on the exercises tab, which is also what carries the participation results.
        CourseExercisesForOverviewDTO exercises = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "course", "courses", courseIdString, "exercises-for-overview").build())
            .retrieve()
            .bodyToMono(CourseExercisesForOverviewDTO.class)
            .block();

        if (course == null && exercises == null) {
            return new RequestStat(now(), System.nanoTime() - start, MISC);
        }

        try {
            // available-tabs is the single source of truth for tab visibility; fall back to the course's own
            // configuration when an older Artemis version does not serve it yet.
            boolean communicationEnabled =
                availableTabs != null
                    ? availableTabs.communication()
                    : course != null &&
                      course.courseInformationSharingConfiguration() != null &&
                      !"DISABLED".equals(course.courseInformationSharingConfiguration());

            if (communicationEnabled) {
                getUnreadMessages();
                getExerciseChannelAndMessages(exerciseId);
            }

            if (exercises != null && exercises.participationResults() != null) {
                for (CourseExercisesForOverviewDTO.ParticipationResultDTO result : exercises.participationResults()) {
                    getLatestResult(result.participationId());
                }
            }
        } catch (Exception e) {
            log.error("Error while getting course overview for {{}}: {{}}", username, e.getMessage());
        }

        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private void getUnreadMessages() {
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "communication", "courses", courseIdString, "unread-messages").build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Get the notification settings for the user in the given course.
     * @return the request stat
     */
    public RequestStat getNotificationSettings() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "notification", "courses", courseIdString, "settings").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    /**
     * Get the notification info for the user.
     * @return the request stat
     */
    public RequestStat getNotificationInfo() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "notification", "courses", "info").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private void getExerciseChannelAndMessages(long exerciseId) {
        Map<String, Object> channelResponse = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "communication", "courses", courseIdString, "exercises", String.valueOf(exerciseId), "channel")
                    .build()
            )
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
        final long channelId;
        if (channelResponse != null) {
            channelId = ((Number) channelResponse.get("id")).longValue();
            if (channelId == 0) {
                return;
            }

            webClient
                .get()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "communication", "courses", courseIdString, "messages")
                        .queryParam("courseId", courseIdString)
                        .queryParam("conversationIds", channelId)
                        .queryParam("postSortCriterion", "CREATION_DATE")
                        .queryParam("sortingOrder", "DESCENDING")
                        .queryParam("page", 0)
                        .queryParam("size", 50)
                        .build()
                )
                .retrieve()
                .toBodilessEntity()
                .block();
        }
    }

    private void getLatestResult(long participationId) {
        webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment(
                        "api",
                        "programming",
                        "programming-exercise-participations",
                        String.valueOf(participationId),
                        "latest-pending-submission"
                    )
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    private RequestStat getCoursesDropdown() {
        long start = System.nanoTime();
        webClient.get().uri("api/course/courses/for-dropdown").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getScienceSettings() {
        long start = System.nanoTime();
        webClient.get().uri("api/atlas/science-settings").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat putScienceEvent(long exerciseId) {
        long start = System.nanoTime();
        try {
            webClient
                .put()
                .uri("api/atlas/science")
                .bodyValue(new ScienceEventDTO(ScienceEventDTO.ScienceEventType.EXERCISE__OPEN, exerciseId))
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.error("Error while putting science event for {{}}: {{}}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getExerciseDetails(long exerciseId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercise", "exercises", String.valueOf(exerciseId), "details").build())
            .retrieve()
            .toBodilessEntity()
            .block();

        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat navigateIntoExam() {
        long start = System.nanoTime();
        StudentExam studentExam = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "own-student-exam").build()
            )
            .retrieve()
            .bodyToMono(StudentExam.class)
            .block();
        var duration = System.nanoTime() - start;

        if (studentExam != null) {
            studentExamId = studentExam.getId();
        }
        return new RequestStat(now(), duration, GET_STUDENT_EXAM);
    }

    private RequestStat getTestExams() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "test-exams-per-user").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getExamSideBarData() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "real-exams-sidebar-data").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat startExam() {
        long start = System.nanoTime();
        studentExam = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment(
                        "api",
                        "exam",
                        "courses",
                        courseIdString,
                        "exams",
                        examIdString,
                        "student-exams",
                        studentExamId.toString(),
                        "conduction"
                    )
                    .build()
            )
            .retrieve()
            .bodyToMono(StudentExam.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, START_STUDENT_EXAM);
    }

    private RequestStat fetchLiveEvents() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "student-exams", "live-events")
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private List<RequestStat> handleExercises() {
        List<RequestStat> requestStats = new ArrayList<>();
        if (studentExam == null || studentExam.getExercises() == null || studentExam.getExercises().isEmpty()) {
            log.warn("No exercises available for {} to handle.", username);
            return requestStats;
        }
        for (var exercise : studentExam.getExercises()) {
            // Opening an exercise is a navigation, and the editor it needs is a chunk the browser has to fetch:
            // the diagram editor for a modeling exercise, the code editor for a programming one.
            requestStats.addAll(loadRouteChunks());
            requestStats.addAll(workOnExercise(exercise));
        }
        return requestStats;
    }

    /**
     * Works on one exercise the way a student does: writing an answer, then leaving it open while the client saves it
     * again and again.
     * <p>
     * The Artemis exam client writes the open submission back every 30 seconds for as long as it has unsaved changes,
     * so a student who spends two minutes on an exercise causes four writes, not one. Submitting once per exercise —
     * what this code used to do — understates submission load by the same factor and hides the write pattern an exam
     * actually produces, where saves from hundreds of students arrive continuously rather than in one burst at the end.
     * <p>
     * Programming and file-upload exercises are left at a single pass: their traffic is a git push or a multipart
     * upload, which a student performs deliberately rather than on a timer.
     *
     * @param exercise the exercise to work on
     * @return one stat per request made
     */
    private List<RequestStat> workOnExercise(Exercise exercise) {
        List<RequestStat> requestStats = new ArrayList<>();
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            requestStats.addAll(solveAndSubmitProgrammingExercise(programmingExercise));
            return requestStats;
        }
        if (exercise instanceof FileUploadExercise fileUploadExercise) {
            requestStats.addAll(solveAndSubmitFileUploadExercise(fileUploadExercise));
            return requestStats;
        }
        for (int save = 0; save < browserSettings.autoSavesPerExercise(); save++) {
            RequestStat stat = null;
            if (exercise instanceof ModelingExercise modelingExercise) {
                stat = solveAndSubmitModelingExercise(modelingExercise);
            } else if (exercise instanceof TextExercise textExercise) {
                stat = solveAndSubmitTextExercise(textExercise);
            } else if (exercise instanceof QuizExercise quizExercise) {
                stat = solveAndSubmitQuizExercise(quizExercise);
            } else {
                return requestStats;
            }
            if (stat == null) {
                // No submission to write for this exercise; repeating would not produce one either.
                return requestStats;
            }
            requestStats.add(paced(stat));
        }
        return requestStats;
    }

    private RequestStat solveAndSubmitModelingExercise(ModelingExercise modelingExercise) {
        var modelingSubmission = getModelingSubmission(modelingExercise);
        if (modelingSubmission != null) {
            if (new Random().nextBoolean()) {
                modelingSubmission.setModel(UMLClassDiagrams.CLASS_MODEL_1);
                modelingSubmission.setExplanationText("The model describes ...");
            } else {
                modelingSubmission.setModel(UMLClassDiagrams.CLASS_MODEL_2);
                modelingSubmission.setExplanationText("Random explanation text ...");
            }

            long start = System.nanoTime();
            webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "modeling", "exercises", modelingExercise.getId().toString(), "modeling-submissions")
                        .build()
                )
                .bodyValue(modelingSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private RequestStat solveAndSubmitTextExercise(TextExercise textExercise) {
        var textSubmission = getTextSubmission(textExercise);
        if (textSubmission != null) {
            textSubmission.setText(LoremIpsum.getInstance().getParagraphs(2, 4));
            textSubmission.setLanguage(Language.ENGLISH);

            long start = System.nanoTime();
            webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "text", "exercises", textExercise.getId().toString(), "text-submissions").build()
                )
                .bodyValue(textSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private RequestStat solveAndSubmitQuizExercise(QuizExercise quizExercise) {
        var quizSubmission = getQuizSubmission(quizExercise);
        // TODO: change something in the quiz submission
        if (quizSubmission != null) {
            long start = System.nanoTime();
            webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "quiz", "exercises", quizExercise.getId().toString(), "submissions", "exam").build()
                )
                .bodyValue(quizSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private void commitAndPush(
        List<RequestStat> requestStats,
        ArtemisAuthMechanism mechanism,
        Long participationId,
        String changedFileContent
    ) throws IOException, GitAPIException {
        if (Objects.requireNonNull(mechanism) == ArtemisAuthMechanism.ONLINE_IDE) {
            makeOnlineIDECommitAndPush(requestStats, participationId, changedFileContent);
        } else {
            makeOfflineIDECommitAndPush(requestStats);
        }
    }

    private List<RequestStat> solveAndSubmitProgrammingExercise(ProgrammingExercise programmingExercise) {
        var programmingParticipation = (ProgrammingExerciseStudentParticipation) programmingExercise
            .getStudentParticipations()
            .iterator()
            .next();
        List<RequestStat> requestStats = new ArrayList<>();
        var repositoryCloneUrl = programmingParticipation.getRepositoryUri();
        var participationId = programmingParticipation.getId();
        requestStats.add(paced(fetchParticipationVcsAccessToken(participationId)));
        requestStats.add(paced(fetchProgrammingIdeSettings()));
        requestStats.add(paced(postParticipation(programmingExercise.getId())));
        // Mirror the real client: subscribe to the personal programming submission/result topics so build
        // results are pushed over the open exam websocket instead of being polled.
        subscribeProgrammingWebsocketTopics();
        try {
            long start = System.nanoTime();

            switch (authenticationMechanism) {
                case ONLINE_IDE -> makeInitialProgrammingExerciseOnlineIDECalls(requestStats, participationId);
                case SSH -> requestStats.add(paced(cloneRepoOverSSH(repositoryCloneUrl)));
                default -> requestStats.add(paced(cloneRepo(repositoryCloneUrl)));
            }

            int n = new Random().nextInt(numberOfCommitsAndPushesFrom, numberOfCommitsAndPushesTo); // we do a random number of commits and pushes to make some noise
            log.info("Commit and push {}x for {}", n, username);
            for (int j = 0; j < n; j++) {
                sleep(100);
                var makeInvalidChange = new Random().nextBoolean();
                var writeToFile = !this.authenticationMechanism.equals(ArtemisAuthMechanism.ONLINE_IDE);
                var changedFileContent = changeFiles(makeInvalidChange, writeToFile);

                commitAndPush(requestStats, this.authenticationMechanism, participationId, changedFileContent);
            }
            log.debug("    Clone and commit+push done in {}", formatDurationFrom(start));
        } catch (Exception e) {
            log.error("Error while handling programming exercise for {{}}: {{}}", username, e.getMessage());
        }
        return requestStats;
    }

    private List<RequestStat> solveAndSubmitFileUploadExercise(FileUploadExercise fileUploadExercise) {
        List<RequestStat> requestStats = new ArrayList<>();
        long start = System.nanoTime();
        var participation = fileUploadExercise.getStudentParticipations().iterator().next();
        webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "fileupload", "participations", participation.getId().toString(), "file-upload-editor")
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC));

        int fileSizeInBytes = 1024 * 1024; // 1 MB file size for file upload exercise
        ByteArrayResource file = FileGeneratorUtil.getDummyFile(fileSizeInBytes, "test-file.txt");
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", file);
        multipartBody.add("submission", new FileUploadSubmission());

        start = System.nanoTime();
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "fileupload", "exercises", fileUploadExercise.getId().toString(), "file-upload-submissions")
                    .build()
            )
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartBody))
            .retrieve()
            .toBodilessEntity()
            .block();
        // TODO maybe this should get a own RequestType to not skew the other submissions? File upload is likely inherently slower
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE));

        return requestStats;
    }

    private RequestStat fetchParticipationVcsAccessToken(Long participationId) {
        long start = System.nanoTime();
        this.participationVcsAccessToken = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "account", "participation-vcs-access-token")
                    .queryParam("participationId", participationId)
                    .build()
            )
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, FETCH_PARTICIPATION_VCS_ACCESS_TOKEN);
    }

    private RequestStat fetchProgrammingIdeSettings() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "programming", "ide-settings").build())
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat submitStudentExam() {
        if (studentExam == null) {
            log.warn("Cannot submit exam for {}: student exam missing.", username);
            return null;
        }
        long start = System.nanoTime();
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "student-exams", "submit").build()
            )
            .bodyValue(studentExam)
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, SUBMIT_STUDENT_EXAM);
    }

    private List<RequestStat> ensureStudentExamLoaded() {
        List<RequestStat> requestStats = new ArrayList<>();
        if (studentExam != null) {
            return requestStats;
        }
        if (studentExamId == null) {
            log.debug("Student exam id missing for {}, fetching own student exam.", username);
            requestStats.add(paced(navigateIntoExam()));
        }
        if (studentExamId == null) {
            log.warn("Student exam id still missing for {}", username);
            return requestStats;
        }
        requestStats.add(paced(startExam()));
        if (studentExam == null) {
            log.warn("Student exam conduction returned no data for {} (studentExamId={})", username, studentExamId);
        }
        return requestStats;
    }

    private RequestStat loadExamSummary() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment(
                        "api",
                        "exam",
                        "courses",
                        courseIdString,
                        "exams",
                        examIdString,
                        "student-exams",
                        studentExamId.toString(),
                        "summary"
                    )
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    @Nullable
    private static ModelingSubmission getModelingSubmission(ModelingExercise modelingExercise) {
        return getSubmissionOfType(modelingExercise, ModelingSubmission.class);
    }

    @Nullable
    private static TextSubmission getTextSubmission(TextExercise textExercise) {
        return getSubmissionOfType(textExercise, TextSubmission.class);
    }

    @Nullable
    private static QuizSubmission getQuizSubmission(QuizExercise quizExercise) {
        return getSubmissionOfType(quizExercise, QuizSubmission.class);
    }

    @Nullable
    private static <S extends Submission> S getSubmissionOfType(Exercise exercise, Class<S> submissionType) {
        if (!exercise.getStudentParticipations().isEmpty()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (!participation.getSubmissions().isEmpty()) {
                var submission = participation.getSubmissions().iterator().next();
                if (submission.getClass().equals(submissionType)) {
                    return (S) submission;
                }
            }
        }
        return null;
    }

    private RequestStat commitAndPushRepo() throws IOException, GitAPIException {
        var localPath = Path.of("repos", username);
        log.debug("Commit and push to {}", localPath);

        var git = Git.open(localPath.toFile());
        git.add().addFilepattern("src").call();
        git.commit().setMessage("local test").setAllowEmpty(true).setSign(false).call();

        var keyPair = loadKeys(privateKeyString);
        long start = System.nanoTime();

        switch (this.authenticationMechanism) {
            case ONLINE_IDE -> throw new IllegalStateException("Cannot push to Online IDE via jgit");
            case PASSWORD -> git.push().setCredentialsProvider(getCredentialsProvider()).call();
            case PARTICIPATION_TOKEN -> git.push().setCredentialsProvider(getCredentialsProviderWithToken()).call();
            case SSH -> git.push()
                .setTransportConfigCallback(transport -> {
                    SshTransport sshTransport = (SshTransport) transport;
                    sshTransport.setSshSessionFactory(getSessionFactory(keyPair));
                })
                .call();
        }

        long duration = System.nanoTime() - start;

        git.close();

        return switch (this.authenticationMechanism) {
            case PASSWORD -> new RequestStat(now(), duration, PUSH_PASSWORD);
            case PARTICIPATION_TOKEN -> new RequestStat(now(), duration, PUSH_TOKEN);
            case SSH -> new RequestStat(now(), duration, PUSH_SSH);
            default -> new RequestStat(now(), duration, PUSH);
        };
    }

    private String changeFiles(boolean invalidChange, boolean writeToFile) throws IOException {
        // TODO: produce larger and more realistic commits
        // Must match the exam programming exercise's packageName (see SimulatedArtemisAdmin#createExamExercises
        // and the online-IDE path below); the template repo places sources under src/progforbenchtemp.
        var bubbleSort = Path.of("repos", username, "src", "progforbenchtemp", "BubbleSort.java");
        log.debug("Change file  {}", bubbleSort);
        var newContent = """
        package progforbenchtemp;


        import java.util.*;


        public class BubbleSort {

            /**
             * BubbleSort
             *
             * @param BubbleSort
             */
            public void performSort(final List<Date> input) {


                //TODO: implement BubbleSort NOW $$1


            }
        }
        """;
        if (invalidChange) {
            newContent += "}";
        }

        newContent = newContent.replace("$$1", String.valueOf(new Random().nextInt(100)));
        if (writeToFile) {
            Files.writeString(bubbleSort, newContent, Charset.defaultCharset());
        }
        return newContent;
    }

    private void makeInitialProgrammingExerciseOnlineIDECalls(List<RequestStat> requestStats, Long participationId) {
        requestStats.add(paced(getLatestResultWithFeedback(participationId)));
        if (latestResultId != null) {
            requestStats.add(paced(getResultDetails(participationId, latestResultId)));
        }
        requestStats.add(paced(fetchRepository(participationId)));
        requestStats.add(paced(fetchPlantUml()));
        requestStats.add(paced(fetchFiles(participationId)));
    }

    private void makeOfflineIDECommitAndPush(List<RequestStat> requestStats) throws IOException, GitAPIException {
        requestStats.add(paced(commitAndPushRepo()));
    }

    private void makeOnlineIDECommitAndPush(List<RequestStat> requestStats, Long participationId, String changedFileContent) {
        requestStats.add(paced(fetchRepository(participationId)));

        var fileName = String.join("/", "src", "progforbenchtemp", "BubbleSort.java");
        requestStats.add(paced(fetchFile(participationId, fileName)));

        log.debug("Commit and push to {}", fileName);
        long start = System.nanoTime();
        webClient
            .put()
            .uri("api/programming/participations/" + participationId + "/repository/files?commit=yes")
            .bodyValue(List.of(new OnlineIdeFileSubmission(fileName, changedFileContent)))
            .retrieve()
            .toBodilessEntity()
            .block();
        long duration = System.nanoTime() - start;
        requestStats.add(new RequestStat(now(), duration, PUSH));
    }

    private RequestStat getLatestResultWithFeedback(Long participationId) {
        long start = System.nanoTime();
        this.latestResultId = null;
        try {
            Map<String, Object> result = webClient
                .get()
                .uri(
                    "api/programming/programming-exercise-participations/" +
                        participationId +
                        "/latest-result-with-feedbacks?withSubmission=true"
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
            if (result != null && result.get("id") instanceof Number resultId) {
                this.latestResultId = resultId.longValue();
            }
        } catch (Exception e) {
            log.debug("Could not fetch latest result with feedback for {}: {}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, PROGRAMMING_EXERCISE_RESULT);
    }

    private RequestStat getResultDetails(long participationId, long resultId) {
        long start = System.nanoTime();
        try {
            webClient
                .get()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment(
                            "api",
                            "assessment",
                            "participations",
                            String.valueOf(participationId),
                            "results",
                            String.valueOf(resultId),
                            "details"
                        )
                        .build()
                )
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.debug("Could not fetch result details for {}: {}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, PROGRAMMING_EXERCISE_RESULT);
    }

    private RequestStat fetchRepository(Long participationId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri("api/programming/participations/" + participationId + "/repository")
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_INFO);
    }

    private RequestStat fetchFile(Long participationId, String fileName) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri("api/programming/participations/" + participationId + "/repository/file?file=" + fileName)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILES);
    }

    private RequestStat fetchFiles(Long participationId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri("api/programming/participations/" + participationId + "/repository/files")
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILES);
    }

    private RequestStat fetchPlantUml() {
        long start = System.nanoTime();
        String plantUmlString =
            "%40startuml%0A%0Aclass%20Client%20%7B%0A%7D%0A%0Aclass%20Policy%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2Bconfigure()%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20Context%20%7B%0A%20%20%3Ccolor%3Agrey%3E-dates%3A%20List%3CDate%3E%3C%2Fcolor%3E%0A%20%20%3Ccolor%3Agrey%3E%2Bsort()%3C%2Fcolor%3E%0A%7D%0A%0Ainterface%20SortStrategy%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20BubbleSort%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20MergeSort%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0AMergeSort%20-up-%7C%3E%20SortStrategy%20%23grey%0ABubbleSort%20-up-%7C%3E%20SortStrategy%20%23grey%0APolicy%20-right-%3E%20Context%20%23grey%3A%20context%0AContext%20-right-%3E%20SortStrategy%20%23grey%3A%20sortAlgorithm%0AClient%20.down.%3E%20Policy%0AClient%20.down.%3E%20Context%0A%0Ahide%20empty%20fields%0Ahide%20empty%20methods%0A%0A%40enduml&useDarkTheme=true";
        webClient
            .get()
            .uri("api/programming/plantuml/svg?plantuml=" + plantUmlString)
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat cloneRepo(String repositoryUrl) throws IOException {
        log.debug("Clone {}", repositoryUrl);

        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());

        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                long start = System.nanoTime();
                UsernamePasswordCredentialsProvider credentialsProvider;
                switch (authenticationMechanism) {
                    case ONLINE_IDE -> throw new IOException("Cannot pull from Online IDE");
                    case PASSWORD -> credentialsProvider = getCredentialsProvider();
                    case PARTICIPATION_TOKEN -> credentialsProvider = getCredentialsProviderWithToken();
                    default -> throw new IllegalStateException("Not implemented");
                }

                var git = Git.cloneRepository()
                    .setURI(repositoryUrl)
                    .setDirectory(localPath.toFile())
                    .setCredentialsProvider(credentialsProvider)
                    .call();

                var duration = System.nanoTime() - start;
                git.close();
                log.debug("Done {}", repositoryUrl);
                return switch (authenticationMechanism) {
                    case PASSWORD -> new RequestStat(now(), duration, CLONE_PASSWORD);
                    case PARTICIPATION_TOKEN -> new RequestStat(now(), duration, CLONE_TOKEN);
                    default -> new RequestStat(now(), duration, CLONE);
                };
            } catch (Exception e) {
                log.warn("Error while cloning repository for {{}}: {{}}", username, e.getMessage());
                attempt++;
                try {
                    sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("Failed to clone repository for {{}}", username);
        throw new RuntimeException("Failed to clone repository for " + username);
    }

    /**
     * Clones a repository over SSH using JGit and measures the time taken for the operation.
     *
     * @param repositoryUrl the URL of the repository to clone
     * @return a RequestStat containing the time taken for the clone operation
     * @throws IOException if an I/O error occurs
     */
    public RequestStat cloneRepoOverSSH(String repositoryUrl) throws IOException {
        log.debug("Clone {}", repositoryUrl);

        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());

        var sshRepositoryUrl = getSshCloneUrl(repositoryUrl);

        int attempt = 0;

        var keyPair = loadKeys(privateKeyString);
        while (attempt < MAX_RETRIES) {
            try {
                long start = System.nanoTime();

                Git git = Git.cloneRepository()
                    .setURI(sshRepositoryUrl)
                    .setDirectory(localPath.toFile())
                    .setTransportConfigCallback(transport -> {
                        SshTransport sshTransport = (SshTransport) transport;
                        sshTransport.setSshSessionFactory(getSessionFactory(keyPair));
                    })
                    .call();

                var duration = System.nanoTime() - start;

                git.close();

                return new RequestStat(now(), duration, CLONE_SSH);
            } catch (Exception e) {
                log.warn("Error while cloning repository for {{}}: {{}}", username, e.getMessage());
                attempt++;
                try {
                    sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.error("Failed to clone repository for {{}}", username);
        throw new RuntimeException("Failed to clone repository for " + username);
    }

    /**
     * Loads SSH keys from a given private key string.
     *
     * @param privateKey the private key in PEM format
     * @return an iterable of KeyPair objects
     */
    public Iterable<KeyPair> loadKeys(String privateKey) {
        try {
            Object parsed = new PEMParser(StringReader.of(privateKey)).readObject();
            KeyPair pair;
            pair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) parsed);

            return Set.of(pair);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SSH keys", e);
        }
    }

    /**
     * Creates an SshdSessionFactory for SSH connections using the provided key pairs.
     *
     * @param keyPairs the key pairs to use for authentication
     * @return an SshdSessionFactory configured with the provided key pairs
     */
    private SshdSessionFactory getSessionFactory(Iterable<KeyPair> keyPairs) {
        // Create a temporary directory to use for the home directory and SSH directory
        // This is required by the SshdSessionFactory object despite us not using them
        Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory("ssh-temp-dir-user-1");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory", e);
        }

        return (
            new SshdSessionFactoryBuilder()
                .setPreferredAuthentications("publickey")
                .setDefaultKeysProvider(ignoredSshDirBecauseWeUseAnInMemorySetOfKeyPairs -> keyPairs)
                .setHomeDirectory(temporaryDirectory.toFile())
                .setSshDirectory(temporaryDirectory.toFile())
                .setServerKeyDatabase((ignoredHomeDir, ignoredSshDir) ->
                    new ServerKeyDatabase() {
                        @Override
                        public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
                            return List.of();
                        }

                        @Override
                        public boolean accept(
                            String connectAddress,
                            InetSocketAddress remoteAddress,
                            PublicKey serverKey,
                            Configuration config,
                            CredentialsProvider provider
                        ) {
                            return true;
                        }
                    }
                )
                //The JGitKeyCache handles the caching of keys to avoid unnecessary disk I/O and improve performance
                .build(new JGitKeyCache())
        );
    }

    /**
     * Create a participation for the given exercise if it does not exist yet.
     *
     * @param exerciseId the ID of the exercise
     * @return the participations for the given exercise
     */
    public RequestStat postParticipation(long exerciseId) {
        long start = System.nanoTime();
        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercise", "exercises", String.valueOf(exerciseId), "participations").build())
            .retrieve()
            .bodyToMono(Participation.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private String getSshCloneUrl(String cloneUrl) {
        var artemisServerHostname = artemisUrl
            .substring(artemisUrl.indexOf("//") + 2)
            .split("/")[0]
            .split(":")[0];
        return "ssh://git@" + artemisServerHostname + ":7921" + cloneUrl.substring(cloneUrl.indexOf("/git/"));
    }

    private RequestStat getIrisStatus(long courseId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "iris", "courses", String.valueOf(courseId), "status").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getIrisChatHistory(long courseId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "iris", "chat", "courses", String.valueOf(courseId), "sessions", "overview").build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProviderWithToken() {
        return new UsernamePasswordCredentialsProvider(username, participationVcsAccessToken);
    }
}
