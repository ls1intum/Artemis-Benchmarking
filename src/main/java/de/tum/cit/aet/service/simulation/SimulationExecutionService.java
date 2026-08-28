package de.tum.cit.aet.service.simulation;

import static java.lang.Thread.sleep;

import de.tum.cit.aet.artemisModel.ArtemisAuthMechanism;
import de.tum.cit.aet.artemisModel.Course;
import de.tum.cit.aet.artemisModel.Exam;
import de.tum.cit.aet.artemisModel.ProgrammingExercise;
import de.tum.cit.aet.domain.*;
import de.tum.cit.aet.repository.LogMessageRepository;
import de.tum.cit.aet.repository.SimulationRunRepository;
import de.tum.cit.aet.service.CiStatusService;
import de.tum.cit.aet.service.MailService;
import de.tum.cit.aet.service.artemis.ArtemisConfiguration;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisStudent;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import de.tum.cit.aet.service.artemis.interaction.browser.BrowserSimulationSettings;
import de.tum.cit.aet.service.artemis.interaction.browser.StaticAssetCatalog;
import de.tum.cit.aet.service.artemis.passkey.ArtemisPasskeyService;
import de.tum.cit.aet.util.ArtemisAccountDTO;
import de.tum.cit.aet.util.ArtemisServer;
import de.tum.cit.aet.util.SimulationConcurrency;
import de.tum.cit.aet.web.websocket.SimulationWebsocketService;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for executing simulations.
 */
@Service
public class SimulationExecutionService {

    private final Logger log = LoggerFactory.getLogger(SimulationExecutionService.class);

    /**
     * Ceiling on how many simulated students may be in flight at once, regardless of how many a run asks for.
     * <p>
     * The default is deliberately conservative. Raise it once the tool host has been shown to have the CPU, memory and
     * disk to back it, lower it to find the point at which a system starts to bend. See {@link SimulationConcurrency}
     * for why the previous core count based limit was wrong and why its replacement is still bounded.
     */
    @Value("${benchmarking.simulation.static-resources.enabled:" + BrowserSimulationSettings.DEFAULT_STATIC_RESOURCES_ENABLED + "}")
    private boolean staticResourcesEnabled;

    @Value(
        "${benchmarking.simulation.static-resources.cold-cache-percentage:" + BrowserSimulationSettings.DEFAULT_COLD_CACHE_PERCENTAGE + "}"
    )
    private int coldCachePercentage;

    @Value("${benchmarking.simulation.static-resources.max-assets:" + BrowserSimulationSettings.DEFAULT_MAX_ASSETS + "}")
    private int maxAssets;

    @Value("${benchmarking.simulation.static-resources.fetch-concurrency:" + BrowserSimulationSettings.DEFAULT_FETCH_CONCURRENCY + "}")
    private int fetchConcurrency;

    @Value("${benchmarking.simulation.auto-saves-per-exercise:" + BrowserSimulationSettings.DEFAULT_AUTO_SAVES_PER_EXERCISE + "}")
    private int autoSavesPerExercise;

    @Value(
        "${benchmarking.simulation.static-resources.assets-per-navigation:" + BrowserSimulationSettings.DEFAULT_ASSETS_PER_NAVIGATION + "}"
    )
    private int assetsPerNavigation;

    @Value(
        "${benchmarking.simulation.server-time-calls-per-navigation:" +
            BrowserSimulationSettings.DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION +
            "}"
    )
    private int serverTimeCallsPerNavigation;

    @Value("${benchmarking.simulation.think-time.min-millis:" + SimulationConcurrency.DEFAULT_MIN_THINK_TIME_MILLIS + "}")
    private long minThinkTimeMillis;

    @Value("${benchmarking.simulation.think-time.max-millis:" + SimulationConcurrency.DEFAULT_MAX_THINK_TIME_MILLIS + "}")
    private long maxThinkTimeMillis;

    @Value("${benchmarking.simulation.arrival-spread-millis:" + SimulationConcurrency.DEFAULT_ARRIVAL_SPREAD_MILLIS + "}")
    private long arrivalSpreadMillis;

    @Value("${benchmarking.simulation.exercise-skip-percentage:" + BrowserSimulationSettings.DEFAULT_EXERCISE_SKIP_PERCENTAGE + "}")
    private int exerciseSkipPercentage;

    /**
     * Empty means "not configured", and falls back to the built-in list rather than to excluding nothing: a typo in
     * the property should not silently hand every student the instructor's console again.
     */
    @Value("${benchmarking.simulation.static-resources.non-student-routes:}")
    private List<String> nonStudentRoutes;

    @Value("${benchmarking.simulation.max-concurrency:" + SimulationConcurrency.DEFAULT_MAX_CONCURRENCY + "}")
    private int maxConcurrency;

    private final SimulationWebsocketService simulationWebsocketService;
    private final ArtemisUserService artemisUserService;
    private final ArtemisPasskeyService artemisPasskeyService;
    private final ArtemisConfiguration artemisConfiguration;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationResultService simulationResultService;
    private final LogMessageRepository logMessageRepository;
    private final MailService mailService;
    private final CiStatusService ciStatusService;
    private boolean doNotSleep = false;

    public SimulationExecutionService(
        ArtemisConfiguration artemisConfiguration,
        SimulationWebsocketService simulationWebsocketService,
        ArtemisUserService artemisUserService,
        SimulationRunRepository simulationRunRepository,
        SimulationResultService simulationResultService,
        LogMessageRepository logMessageRepository,
        MailService mailService,
        CiStatusService ciStatusService,
        ArtemisPasskeyService artemisPasskeyService
    ) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationResultService = simulationResultService;
        this.logMessageRepository = logMessageRepository;
        this.artemisUserService = artemisUserService;
        this.mailService = mailService;
        this.ciStatusService = ciStatusService;
        this.artemisPasskeyService = artemisPasskeyService;
    }

    /**
     * Executes the given simulation run. This method is synchronized to prevent multiple simulations from running at the same time.
     * <p>
     * The steps of the simulation depend on the simulation mode, see {@link Simulation.Mode}.
     * This method sends status updates, log messages and results to the client via websockets.
     *
     * @param simulationRun the simulation run to execute
     * @throws SimulationFailedException if an error occurs while executing the simulation
     */
    public synchronized void simulateExam(SimulationRun simulationRun) {
        ArtemisAccountDTO accountDTO = simulationRun.getAdminAccount();
        SimulationSchedule schedule = simulationRun.getSchedule();

        // Set status to running and save
        simulationRun.setStatus(SimulationRun.Status.RUNNING);
        simulationRun = simulationRunRepository.save(simulationRun);

        // Since schedule is not saved in the database, we need to set it again
        simulationRun.setSchedule(schedule);

        // Tell the client that the simulation run status has changed
        simulationWebsocketService.sendRunStatusUpdate(simulationRun);

        var simulation = simulationRun.getSimulation();
        var courseId = simulation.getCourseId();
        var examId = simulation.getExamId();
        SimulatedArtemisAdmin admin = null;
        SimulatedArtemisStudent[] students;

        // If no admin account is provided, use the instructor credentials if they are provided.
        // The account may be present but empty: the run form submits a DTO whose fields are null when the user leaves
        // them blank, and a client that posts `{}` produces the same thing. Reading through those nulls threw a
        // NullPointerException out of the queue thread, which left the run sitting in RUNNING for ever.
        if (!hasCredentials(accountDTO) && simulation.instructorCredentialsProvided()) {
            accountDTO = new ArtemisAccountDTO();
            accountDTO.setUsername(simulation.getInstructorUsername());
            accountDTO.setPassword(simulation.getInstructorPassword());
        }

        logAndSend(
            false,
            simulationRun,
            "Starting simulation with %d users on %s...",
            simulation.getNumberOfUsers(),
            simulation.getServer().name()
        );

        students = initializeStudents(simulationRun);

        ProgrammingExercise courseProgrammingExercise = null;

        // Initialize admin if necessary
        if (simulation.getMode() != Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM) {
            admin = initializeAdmin(simulationRun, accountDTO);

            Course course;

            // Create course if necessary
            if (simulation.getMode() == Simulation.Mode.CREATE_COURSE_AND_EXAM) {
                course = createCourse(admin, simulationRun);
                courseId = course.getId();
                logAndSend(false, simulationRun, "Successfully created course. Course ID: %d", courseId);

                registerStudentsForCourse(admin, simulationRun, courseId, students);

                if (!doNotSleep && !artemisConfiguration.getIsLocal(simulationRun.getSimulation().getServer())) {
                    // Wait for synchronization of user groups
                    try {
                        logAndSend(false, simulationRun, "Waiting for synchronization of user groups (1 min)...");
                        sleep(1_000 * 60);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                logAndSend(false, simulationRun, "Using existing course.");
                course = getCourse(admin, simulationRun, courseId);
            }

            // Create programming exercise for course, this is needed to simulate some side requests
            courseProgrammingExercise = createCourseProgrammingExercise(admin, simulationRun, course);

            // Create exam if necessary
            if (simulation.getMode() != Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM) {
                Exam exam = createExam(admin, simulationRun, course);
                examId = exam.getId();
                logAndSend(false, simulationRun, "Successfully created exam. Exam ID: %d", examId);

                createExamExercises(admin, simulationRun, courseId, exam);
                registerStudentsForExam(admin, simulationRun, courseId, examId);
            } else {
                logAndSend(false, simulationRun, "Using existing exam.");
            }

            prepareExam(admin, simulationRun, courseId, examId);

            if (!doNotSleep) {
                try {
                    // Wait for a couple of seconds. Without this, students cannot access their repos.
                    // Not sure why this is necessary, trying to figure it out
                    sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            logAndSend(false, simulationRun, "Preparation finished...");
        } else {
            logAndSend(false, simulationRun, "Using existing course and exam. No admin required.");
        }

        // Perform simulation of exam participations
        List<RequestStat> requestStats = simulateExamParticipations(
            simulationRun,
            students,
            admin,
            courseId,
            examId,
            courseProgrammingExercise != null ? courseProgrammingExercise.getId() : 0
        );

        logAndSend(false, simulationRun, "Simulation finished.");

        // Cleanup deletes running build jobs. When it is enabled subscribing to CI status is disabled
        cleanupAsync(admin, simulationRun, courseId, examId);

        // Calculate, save and send result
        SimulationRun runWithResult = simulationResultService.calculateAndSaveResult(simulationRun, requestStats);
        finishSimulationRun(runWithResult);
        sendRunResult(runWithResult);

        if (artemisConfiguration.getIsLocal(simulationRun.getSimulation().getServer())) {
            if (admin == null) {
                try {
                    admin = initializeAdminFromUserManagement(simulationRun.getSimulation().getServer());
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Cannot get CI status, no admin account available.");
                    return;
                }
            }

            if (simulationRun.getSimulation().isCancelBuildJobsAfterRun()) {
                // Cancelling is scoped to a course, not to this run, so it is only ever safe where the run created the
                // course and therefore owns everything in it. Against an existing course it would cancel build jobs
                // belonging to real students, so refuse rather than obey. The client hides the option for those modes,
                // but the REST API takes the flag directly and a scheduled simulation keeps whatever it was saved with.
                if (simulationRun.getSimulation().getMode() != Simulation.Mode.CREATE_COURSE_AND_EXAM) {
                    logAndSend(
                        true,
                        simulationRun,
                        "Not cancelling build jobs: this run used an existing course, and cancelling covers the whole course rather than just this run."
                    );
                } else {
                    // Cancelling and tracking are mutually exclusive: a cancelled build job never produces a result, so
                    // tracking would wait for jobs that are never coming. Skip it and say so, rather than reporting CI
                    // figures that would only describe how far the queue happened to get.
                    cancelBuildJobsOfCourse(admin, simulationRun, courseId);
                    return;
                }
            }

            // Subscribe to CI status, as we can only safely delete the course after all CI jobs have finished.
            if (!artemisConfiguration.getCleanup(simulationRun.getSimulation().getServer())) {
                try {
                    ciStatusService.subscribeToCiStatusViaResults(runWithResult, admin, examId).get();
                } catch (ExecutionException | InterruptedException e) {
                    logAndSend(true, simulationRun, "Error while subscribing to CI status: %s", e.getMessage());
                }
            }
        }
    }

    /**
     * Whether an account carries a username and a password that could be used to log in.
     *
     * @param accountDTO the account to check, possibly null or possibly present with null fields
     * @return true only if both are set and non-blank
     */
    private static boolean hasCredentials(ArtemisAccountDTO accountDTO) {
        return (
            accountDTO != null &&
            accountDTO.getUsername() != null &&
            !accountDTO.getUsername().isBlank() &&
            accountDTO.getPassword() != null &&
            !accountDTO.getPassword().isBlank()
        );
    }

    /**
     * Cancel the build jobs this run queued, so the agents are idle again before the next run starts.
     * <p>
     * Scoped to the course the run created, so a shared server keeps processing everything else. A failure here is
     * logged but does not fail the run: the measurement is already complete and saved by this point, and leaving the
     * queue to drain on its own is a slower outcome rather than a wrong one.
     *
     * @param admin         the admin or instructor performing the cancellation
     * @param simulationRun the run whose build jobs should be cancelled
     * @param courseId      the course the run created
     */
    private void cancelBuildJobsOfCourse(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId) {
        try {
            logAndSend(false, simulationRun, "Cancelling the build jobs queued by this run...");
            admin.cancelBuildJobsOfCourse(courseId);
            logAndSend(false, simulationRun, "Build jobs cancelled. CI status is not reported for a run that cancels its build jobs.");
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Could not cancel the build jobs of this run: %s", e.getMessage());
        }
    }

    /**
     * Performs the simulation of parallelized exam participations for the given students.
     * This includes logging in, performing initial calls and participating in the exam.
     * The statistics of the performed requests are collected and returned as a list.
     * <p>
     * Fails the simulation run if an error occurs while performing the simulations.
     * Does not fail for exceptions occurring for individual students.
     *
     * @param simulationRun the simulation run to perform the exam participations for
     * @param students      the students to perform the exam participations with
     * @param admin         the admin to use for cleanup if necessary
     * @param courseId      the ID of the course the exam is in
     * @param examId        the ID of the exam to participate in
     * @return a list of request stats for all performed actions
     * @throws SimulationFailedException if an error occurs while performing the simulations
     */
    private List<RequestStat> simulateExamParticipations(
        SimulationRun simulationRun,
        SimulatedArtemisStudent[] students,
        SimulatedArtemisAdmin admin,
        long courseId,
        long examId,
        long programmingExerciseId
    ) {
        logAndSend(false, simulationRun, "Starting simulation...");
        Simulation simulation = simulationRun.getSimulation();

        int concurrency = SimulationConcurrency.concurrencyFor(simulation.getNumberOfUsers(), maxConcurrency);
        logAndSend(false, simulationRun, "Simulating up to %d students at the same time.", concurrency);

        try {
            logAndSend(false, simulationRun, "Logging in students...");
            List<RequestStat> requestStats = new ArrayList<>(
                performActionWithAll(concurrency, simulation.getNumberOfUsers(), i -> students[i].login())
            );

            // Deliberately its own phase, ahead of every measured request. Students used to pull the client bundle as
            // they navigated, so at cohort scale the REST timings measured the queue behind ten gigabytes of
            // JavaScript instead of the endpoints. Downloading first separates the two loads; each student's browser
            // cache then makes the navigations that follow free, as a real browser's does.
            logAndSend(false, simulationRun, "Downloading the client bundle...");
            requestStats.addAll(performActionWithAll(concurrency, simulation.getNumberOfUsers(), i -> students[i].loadClientBundle()));

            logAndSend(false, simulationRun, "Performing initial calls...");
            requestStats.addAll(performActionWithAll(concurrency, simulation.getNumberOfUsers(), i -> students[i].performInitialCalls()));

            logAndSend(false, simulationRun, "Participating in exam...");
            requestStats.addAll(
                performActionWithAll(concurrency, simulation.getNumberOfUsers(), i ->
                    students[i].startExamParticipation(courseId, examId, programmingExerciseId)
                )
            );

            // create ci status here and start measuring the total duration of build jobs since Artemis starts to process the queue directly
            CiStatus status = ciStatusService.createCiStatus(simulationRun);
            simulationRun.setCiStatus(status);

            requestStats.addAll(
                performActionWithAll(concurrency, simulation.getNumberOfUsers(), i -> students[i].participateInExam(courseId, examId))
            );
            requestStats.addAll(
                performActionWithAll(concurrency, simulation.getNumberOfUsers(), i -> students[i].submitAndEndExam(courseId, examId))
            );

            return requestStats;
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while performing simulation: %s", e.getMessage());
            cleanupAsync(admin, simulationRun, courseId, examId);
            failSimulationRun(simulationRun);
            throw new SimulationFailedException("Error while performing simulation", e);
        }
    }

    /**
     * Sets the doNotSleep flag.
     * If the flag is set to true, the simulation will not wait for user group synchronization.
     * The flag should only be set to true for testing purposes when the connection to Artemis is mocked.
     *
     * @param doNotSleep the value to set the flag to
     */
    public void setDoNotSleep(boolean doNotSleep) {
        this.doNotSleep = doNotSleep;
    }

    /**
     * Initializes and logs in the admin for the given simulation run.
     * Fails the simulation run if an error occurs while initializing the admin.
     *
     * @param simulationRun the simulation run to initialize the admin for
     * @param accountDTO    the account to use for logging in (only necessary for production instance)
     * @return the initialized and logged in admin
     * @throws SimulationFailedException if an error occurs while initializing the admin
     */
    private SimulatedArtemisAdmin initializeAdmin(SimulationRun simulationRun, ArtemisAccountDTO accountDTO) {
        logAndSend(false, simulationRun, "Initializing admin...");
        Simulation simulation = simulationRun.getSimulation();
        try {
            if (simulation.getServer() == ArtemisServer.PRODUCTION) {
                if (accountDTO == null || accountDTO.getUsername() == null || accountDTO.getUsername().isBlank()) {
                    logAndSend(true, simulationRun, "No admin account provided for production server.");
                } else {
                    logAndSend(
                        false,
                        simulationRun,
                        "Using provided admin account '%s' for %s.",
                        accountDTO.getUsername(),
                        simulation.getServer()
                    );
                }
                return initializeAdminWithAccount(simulation.getServer(), accountDTO);
            }

            var adminAccount = artemisUserService.getAdminUser(simulation.getServer());
            if (adminAccount == null) {
                logAndSend(true, simulationRun, "No admin user found in user management for %s.", simulation.getServer());
            } else {
                logAndSend(
                    false,
                    simulationRun,
                    "Using admin account '%s' from user management for %s.",
                    adminAccount.getUsername(),
                    simulation.getServer()
                );
            }
            return initializeAdminFromUserManagement(simulation.getServer(), adminAccount);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while initializing admin: %s", e.getMessage());
            failSimulationRun(simulationRun);
            throw new SimulationFailedException("Error while initializing admin", e);
        }
    }

    /**
     * Initializes the admin for the given server and logs in using the admin account from the user management.
     *
     * @param server the Artemis Server to initialize the admin for
     * @return the initialized and logged in admin
     */
    private SimulatedArtemisAdmin initializeAdminFromUserManagement(ArtemisServer server) {
        var adminAccount = artemisUserService.getAdminUser(server);
        return initializeAdminFromUserManagement(server, adminAccount);
    }

    private SimulatedArtemisAdmin initializeAdminFromUserManagement(ArtemisServer server, ArtemisUser adminAccount) {
        if (adminAccount == null) {
            throw new IllegalStateException("No admin account found for server " + server.name());
        }
        var admin = SimulatedArtemisUser.createArtemisAdminFromUser(artemisConfiguration.getUrl(server), adminAccount, artemisUserService);
        // Where the account has a registered passkey, authenticate with it: Artemis can require a passkey for
        // administrator features, and a password login is refused by every admin endpoint on such a server.
        if (adminAccount.hasPasskey()) {
            admin.setPasskeyService(artemisPasskeyService);
        }
        admin.login();
        return admin;
    }

    /**
     * Initializes the admin for the given server with the given account and logs in.
     *
     * @param server            the Artemis Server to initialize the admin for
     * @param artemisAccountDTO the account to use for logging in
     * @return the initialized and logged in admin
     */
    private SimulatedArtemisAdmin initializeAdminWithAccount(ArtemisServer server, ArtemisAccountDTO artemisAccountDTO) {
        var admin = SimulatedArtemisUser.createArtemisAdminFromCredentials(
            artemisConfiguration.getUrl(server),
            artemisAccountDTO.getUsername(),
            artemisAccountDTO.getPassword()
        );
        admin.login();
        return admin;
    }

    /**
     * Creates a course for the given admin and simulation run.
     * Fails the simulation run if an error occurs while creating the course.
     *
     * @param admin         the admin to use for creating the course
     * @param simulationRun the simulation run to create the course for
     * @return the created course
     * @throws SimulationFailedException if an error occurs while creating the course
     */
    private Course createCourse(SimulatedArtemisAdmin admin, SimulationRun simulationRun) {
        logAndSend(false, simulationRun, "Creating course...");
        try {
            return admin.createCourse();
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while creating course: %s", e.getMessage());
            failSimulationRun(simulationRun);
            throw new SimulationFailedException("Error while creating course", e);
        }
    }

    /**
     * Registers the given students for the given course using the given admin and simulation run.
     * Fails the simulation run if an error occurs while registering the students.
     *
     * @param admin         the admin to use for registering the students
     * @param simulationRun the simulation run to register the students for
     * @param courseId      the ID of the course to register the students for
     * @param students      the students to register
     * @throws SimulationFailedException if an error occurs while registering the students
     */
    private void registerStudentsForCourse(
        SimulatedArtemisAdmin admin,
        SimulationRun simulationRun,
        long courseId,
        SimulatedArtemisStudent[] students
    ) {
        logAndSend(false, simulationRun, "Registering students for course...");
        try {
            admin.registerStudentsForCourse(courseId, students);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while registering students for course: %s", e.getMessage());
            failSimulationRun(simulationRun);
            cleanupAsync(admin, simulationRun, courseId, 0);
            throw new SimulationFailedException("Error while registering students for course", e);
        }
    }

    /**
     * Fetches the course with the given ID using the given admin and simulation run.
     * Fails the simulation run if an error occurs while fetching the course.
     *
     * @param admin         the admin to use for fetching the course
     * @param simulationRun the simulation run to fetch the course for
     * @param courseId      the ID of the course to fetch
     * @return the fetched course
     * @throws SimulationFailedException if an error occurs while fetching the course
     */
    private Course getCourse(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId) {
        try {
            return admin.getCourse(courseId);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while fetching course: %s", e.getMessage());
            failSimulationRun(simulationRun);
            throw new SimulationFailedException("Error while fetching course", e);
        }
    }

    private ProgrammingExercise createCourseProgrammingExercise(SimulatedArtemisAdmin admin, SimulationRun simulationRun, Course course) {
        logAndSend(false, simulationRun, "Creating course programming exercise...");
        try {
            return admin.createCourseProgrammingExercise(course);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while creating course programming exercise: %s", e.getMessage());
            failSimulationRun(simulationRun);
            throw new SimulationFailedException("Error while creating course programming exercise", e);
        }
    }

    /**
     * Creates an exam for the given simulation run in the given course using the given admin.
     * Fails the simulation run if an error occurs while creating the exam.
     *
     * @param admin         the admin to use for creating the exam
     * @param simulationRun the simulation run to create the exam for
     * @param course        the course to create the exam in
     * @return the created exam
     * @throws SimulationFailedException if an error occurs while creating the exam
     */
    private Exam createExam(SimulatedArtemisAdmin admin, SimulationRun simulationRun, Course course) {
        logAndSend(false, simulationRun, "Creating exam...");
        try {
            return admin.createExam(course);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while creating exam: %s", e.getMessage());
            failSimulationRun(simulationRun);
            cleanupAsync(admin, simulationRun, course.getId(), 0);
            throw new SimulationFailedException("Error while creating exam", e);
        }
    }

    /**
     * Creates the exercises for the given exam using the given admin and simulation run.
     * Fails the simulation run if an error occurs while creating the exercises.
     *
     * @param admin         the admin to use for creating the exercises
     * @param simulationRun the simulation run to create the exercises for
     * @param courseId      the ID of the course the exam is in
     * @param exam          the exam to create the exercises for
     * @throws SimulationFailedException if an error occurs while creating the exercises
     */
    private void createExamExercises(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId, Exam exam) {
        logAndSend(false, simulationRun, "Creating exam exercises...");
        try {
            admin.createExamExercises(courseId, exam);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while creating exam exercises: %s", e.getMessage());
            failSimulationRun(simulationRun);
            cleanupAsync(admin, simulationRun, courseId, exam.getId());
            throw new SimulationFailedException("Error while creating exam exercises", e);
        }
    }

    /**
     * Registers the students for the given exam using the given admin and simulation run.
     * Registers all students of the course.
     * Fails the simulation run if an error occurs while registering the students.
     *
     * @param admin         the admin to use for registering the students
     * @param simulationRun the simulation run to register the students for
     * @param courseId      the ID of the course the exam is in
     * @param examId        the ID of the exam to register the students for
     * @throws SimulationFailedException if an error occurs while registering the students
     */
    private void registerStudentsForExam(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId, long examId) {
        logAndSend(false, simulationRun, "Registering students for exam...");
        try {
            admin.registerStudentsForExam(courseId, examId);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while registering students for exam: %s", e.getMessage());
            failSimulationRun(simulationRun);
            cleanupAsync(admin, simulationRun, courseId, examId);
            throw new SimulationFailedException("Error while registering students for exam", e);
        }
    }

    /**
     * Prepares the exam for conduction using the given admin and simulation run.
     * This includes generating the student exams and preparing the exercises.
     * Fails the simulation run if an error occurs while preparing the exam.
     *
     * @param admin         the admin to use for preparing the exam
     * @param simulationRun the simulation run to prepare the exam for
     * @param courseId      the ID of the course the exam is in
     * @param examId        the ID of the exam to prepare
     * @throws SimulationFailedException if an error occurs while preparing the exam
     */
    private void prepareExam(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId, long examId) {
        logAndSend(false, simulationRun, "Preparing exam for conduction...");
        try {
            admin.prepareExam(courseId, examId);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while preparing exam: %s", e.getMessage());
            failSimulationRun(simulationRun);
            cleanupAsync(admin, simulationRun, courseId, examId);
            throw new SimulationFailedException("Error while preparing exam", e);
        }
    }

    /**
     * Initializes the students for the simulation.
     * <p>
     * Note: This method does not log in the students.
     * Fails the simulation run if an error occurs while initializing the students.
     *
     * @param simulationRun the simulationRun to initialize the students for
     * @return an array of initialized students
     * @throws SimulationFailedException if an error occurs while initializing the students
     */
    /**
     * The browser behaviour students in this run should reproduce, taken from configuration.
     *
     * @return the settings to hand to every student of the run
     */
    private BrowserSimulationSettings browserSimulationSettings() {
        return new BrowserSimulationSettings(
            staticResourcesEnabled,
            coldCachePercentage,
            maxAssets,
            fetchConcurrency,
            autoSavesPerExercise,
            assetsPerNavigation,
            nonStudentRoutes == null || nonStudentRoutes.isEmpty()
                ? BrowserSimulationSettings.DEFAULT_NON_STUDENT_ROUTES
                : nonStudentRoutes,
            exerciseSkipPercentage,
            serverTimeCallsPerNavigation
        );
    }

    private SimulatedArtemisStudent[] initializeStudents(SimulationRun simulationRun) {
        List<ArtemisUser> artemisUsers;
        Simulation simulation = simulationRun.getSimulation();

        // The client bundle carries content-hashed filenames, so the list discovered for a previous run is stale as
        // soon as Artemis is redeployed. Re-read it for every run rather than serving 404s to every student.
        StaticAssetCatalog.clear();

        try {
            if (simulation.isCustomizeUserRange()) {
                artemisUsers = artemisUserService.getUsersFromRange(simulation.getServer(), simulation.getUserRange());
            } else {
                artemisUsers = artemisUserService.getUsersFromRange(simulation.getServer(), "1-" + simulation.getNumberOfUsers());
            }

            SimulatedArtemisStudent[] users = new SimulatedArtemisStudent[artemisUsers.size()];
            int onlineIde, password, token, ssh;
            onlineIde = password = token = ssh = 0;

            for (int i = 0; i < artemisUsers.size(); i++) {
                var mechanism = getArtemisAuthMechanism(simulation);
                switch (mechanism) {
                    case ONLINE_IDE -> onlineIde++;
                    case PASSWORD -> password++;
                    case PARTICIPATION_TOKEN -> token++;
                    case SSH -> ssh++;
                }

                users[i] = SimulatedArtemisUser.createArtemisStudent(
                    artemisConfiguration.getUrl(simulation.getServer()),
                    artemisUsers.get(i),
                    artemisUserService,
                    simulation.getNumberOfCommitsAndPushesFrom(),
                    simulation.getNumberOfCommitsAndPushesTo(),
                    mechanism,
                    browserSimulationSettings()
                );
            }

            log.info(
                "Users will use authentication mechanisms: onlineIDE {{}} | password {{}} | token {{}} | SSH {{}}",
                onlineIde,
                password,
                token,
                ssh
            );
            logAndSend(
                false,
                simulationRun,
                "User authentication: onlineIDE %s | password %s | token %s | SSH %s",
                onlineIde,
                password,
                token,
                ssh
            );

            return users;
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while initializing students: %s", e.getMessage());
            failSimulationRun(simulationRun);
            throw new SimulationFailedException("Error while initializing students", e);
        }
    }

    /**
     * Performs the given action for all users in parallel with the given number of threads.
     * Collects all request stats and returns them as a list.
     * <p>
     * If an exception occurs while performing the action for a user, the exception is logged and the user is skipped.
     * Exceptions occurring for one user do not affect the execution of the action for other users and are not rethrown.
     *
     * @param threadCount   the number of threads to use
     * @param numberOfUsers the number of users to perform the action for
     * @param action        the action to perform
     * @return a list of request stats for all performed actions
     */
    private List<RequestStat> performActionWithAll(int concurrency, int numberOfUsers, Function<Integer, List<RequestStat>> action) {
        List<RequestStat> requestStats = Collections.synchronizedList(new ArrayList<>());

        SimulationConcurrency.forEachIndex(
            concurrency,
            numberOfUsers,
            minThinkTimeMillis,
            maxThinkTimeMillis,
            arrivalSpreadMillis,
            (i, thinkTime) -> {
                try {
                    requestStats.addAll(action.apply(i));
                } catch (Exception e) {
                    log.warn("Error while performing action for user {}: {}", i + 1, e.getMessage());
                }
            }
        );

        return requestStats;
    }

    /**
     * Calls {@link #cleanup(SimulatedArtemisAdmin, SimulationRun, long, long)} asynchronously.
     *
     * @param admin         the admin to use for cleanup
     * @param simulationRun the simulation run to cleanup
     * @param courseId      the ID of the course to cleanup
     * @param examId        the ID of the exam to cleanup
     */
    private void cleanupAsync(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId, long examId) {
        if (Thread.currentThread().isInterrupted() || admin == null) {
            return;
        }
        new Thread(() -> cleanup(admin, simulationRun, courseId, examId)).start();
    }

    /**
     * Cleans up the course and exam created for the simulation-run if necessary (depending on the simulation mode).
     * Cleanup is only performed if the cleanup flag is set to true in the application properties.
     * Note that this method can take a while to complete because it waits for the Artemis server to finish the cleanup.
     * <p>
     * It is recommended to call this method asynchronously via {@link #cleanupAsync(SimulatedArtemisAdmin, SimulationRun, long, long)}.
     *
     * @param admin         the admin to use for cleanup
     * @param simulationRun the simulation run to cleanup
     * @param courseId      the ID of the course to cleanup
     * @param examId        the ID of the exam to cleanup
     */
    private void cleanup(SimulatedArtemisAdmin admin, SimulationRun simulationRun, long courseId, long examId) {
        if (Thread.currentThread().isInterrupted() || admin == null) {
            return;
        }

        var server = simulationRun.getSimulation().getServer();
        var mode = simulationRun.getSimulation().getMode();
        if (!artemisConfiguration.getCleanup(server)) {
            logAndSend(false, simulationRun, "Cleanup is currently disabled for this Artemis instance.");
            return;
        }

        // Scoped to this run's own course. Cleanup used to cancel every queued and running build job on the instance,
        // which is indefensible on a shared server: a benchmark tidying up after itself would throw away work that
        // belongs to whoever else is using that Artemis.
        cancelBuildJobsOfCourse(admin, simulationRun, courseId);
        if (!doNotSleep) {
            try {
                sleep(1_000 * 10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        logAndSend(false, simulationRun, "Cleaning up... This may take a while.");
        try {
            if (mode == Simulation.Mode.EXISTING_COURSE_CREATE_EXAM && examId != 0) {
                logAndSend(false, simulationRun, "Deleting exam...");
                admin.deleteExam(courseId, examId);
                logAndSend(false, simulationRun, "Successfully deleted exam.");
            } else if (mode == Simulation.Mode.CREATE_COURSE_AND_EXAM) {
                logAndSend(false, simulationRun, "Deleting course...");
                admin.deleteCourse(courseId);
                logAndSend(false, simulationRun, "Successfully deleted course.");
            } else {
                logAndSend(false, simulationRun, "No cleanup necessary.");
            }
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while cleaning up: %s", e.getMessage());
        }
    }

    /**
     * Logs the given message and sends it to the client via websockets.
     * Also saves the message to the database.
     *
     * @param error         whether the message is an error message
     * @param simulationRun the simulation run to send the message for
     * @param format        the format string
     * @param args          the arguments for the format string
     */
    private void logAndSend(boolean error, SimulationRun simulationRun, String format, Object... args) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        var message = format.formatted(args);
        if (error) {
            log.error(message);
        } else {
            log.info(message);
        }
        if (message.length() > 255) {
            message = message.substring(0, 255);
        }
        LogMessage logMessage = new LogMessage();
        logMessage.setSimulationRun(simulationRun);
        logMessage.setMessage(message);
        logMessage.setError(error);
        logMessage.setTimestamp(ZonedDateTime.now());
        LogMessage savedLogMessage = logMessageRepository.save(logMessage);
        simulationWebsocketService.sendRunLogMessage(simulationRun, savedLogMessage);
    }

    /**
     * Sets the simulation run status to failed and sends a notification to the client via websockets.
     * Also sends a failure mail if the simulation run is part of a schedule.
     *
     * @param simulationRun the simulation run to fail
     */
    /**
     * Marks a run as failed after an error that escaped the run's own handling.
     * <p>
     * Everything {@link #simulateExam} expects to go wrong is caught and reported inside it. Anything that is not
     * leaves the status untouched, so the run stays RUNNING and the queue looks busy for ever: exactly the stall that
     * had to be fixed once already for cancelled build jobs. The queue calls this so an unexpected error ends the run
     * visibly instead.
     *
     * @param simulationRun the run that could not be executed
     * @param error         the error that ended it
     */
    public void failRunAfterUnexpectedError(SimulationRun simulationRun, Throwable error) {
        logAndSend(true, simulationRun, "Simulation run ended unexpectedly: %s", String.valueOf(error.getMessage()));
        failSimulationRun(simulationRun);
    }

    private void failSimulationRun(SimulationRun simulationRun) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        if (simulationRun.getSchedule() != null) {
            LogMessage errorLogMessage = logMessageRepository
                .findBySimulationRunIdAndErrorIsTrue(simulationRun.getId())
                .stream()
                .max(Comparator.comparing(LogMessage::getTimestamp))
                .orElse(null);
            mailService.sendRunFailureMail(simulationRun, simulationRun.getSchedule(), errorLogMessage);
        }
        simulationRun.setStatus(SimulationRun.Status.FAILED);
        simulationRun.setEndDateTime(ZonedDateTime.now());
        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        simulationWebsocketService.sendRunStatusUpdate(savedSimulationRun);
    }

    /**
     * Sets the simulation run status to finished and sends the result to the client via websockets.
     *
     * @param simulationRun the simulation run to finish
     */
    private void finishSimulationRun(SimulationRun simulationRun) {
        simulationRun.setStatus(SimulationRun.Status.FINISHED);
        simulationRun.setEndDateTime(ZonedDateTime.now());
        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        simulationWebsocketService.sendRunStatusUpdate(savedSimulationRun);
    }

    /**
     * Sends the result of the given simulation run to the client via websockets.
     * Also sends a mail with the result if the simulation run is part of a schedule.
     *
     * @param simulationRun the simulation run to send the result for
     */
    private void sendRunResult(SimulationRun simulationRun) {
        simulationWebsocketService.sendSimulationResult(simulationRun);
        if (simulationRun.getSchedule() != null) {
            mailService.sendRunResultMail(simulationRun, simulationRun.getSchedule());
        }
    }

    private ArtemisAuthMechanism getArtemisAuthMechanism(Simulation simulation) {
        Random random = new Random();
        double randomValue = random.nextDouble() * 100;

        if (randomValue <= simulation.getOnlineIdePercentage()) {
            return ArtemisAuthMechanism.ONLINE_IDE;
        } else if (randomValue <= simulation.getOnlineIdePercentage() + simulation.getPasswordPercentage()) {
            return ArtemisAuthMechanism.PASSWORD;
        } else if (
            randomValue <= simulation.getOnlineIdePercentage() + simulation.getPasswordPercentage() + simulation.getTokenPercentage()
        ) {
            return ArtemisAuthMechanism.PARTICIPATION_TOKEN;
        } else {
            return ArtemisAuthMechanism.SSH;
        }
    }
}
