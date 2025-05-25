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
import de.tum.cit.aet.util.ArtemisAccountDTO;
import de.tum.cit.aet.util.ArtemisServer;
import de.tum.cit.aet.web.websocket.SimulationWebsocketService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for executing simulations.
 */
@Service
public class SimulationExecutionService {

    private final Logger log = LoggerFactory.getLogger(SimulationExecutionService.class);

    private final SimulationWebsocketService simulationWebsocketService;
    private final ArtemisUserService artemisUserService;
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
        CiStatusService ciStatusService
    ) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationResultService = simulationResultService;
        this.logMessageRepository = logMessageRepository;
        this.artemisUserService = artemisUserService;
        this.mailService = mailService;
        this.ciStatusService = ciStatusService;
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

        // If no admin account is provided, use the instructor credentials if they are provided
        if (
            (accountDTO == null || accountDTO.getUsername().isBlank() || accountDTO.getPassword().isBlank()) &&
            simulation.instructorCredentialsProvided()
        ) {
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

        int threadCount = Integer.min(Runtime.getRuntime().availableProcessors() * 10, simulation.getNumberOfUsers());
        logAndSend(false, simulationRun, "Using %d threads for simulation.", threadCount);

        List<RequestStat> requestStats = new ArrayList<>();

        try {
            logAndSend(false, simulationRun, "Logging in students...");
            requestStats.addAll(performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].login()));

            logAndSend(false, simulationRun, "Performing initial calls...");
            requestStats.addAll(performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].performInitialCalls()));

            logAndSend(false, simulationRun, "Participating in exam...");
            requestStats.addAll(
                performActionWithAll(threadCount, simulation.getNumberOfUsers(), i ->
                    students[i].startExamParticipation(courseId, examId, programmingExerciseId)
                )
            );

            // create ci status here and start measuring the total duration of build jobs since Artemis starts to process the queue directly
            CiStatus status = ciStatusService.createCiStatus(simulationRun);
            simulationRun.setCiStatus(status);

            requestStats.addAll(
                performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].participateInExam(courseId, examId))
            );
            requestStats.addAll(
                performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].submitAndEndExam(courseId, examId))
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
            return simulation.getServer() == ArtemisServer.PRODUCTION
                ? initializeAdminWithAccount(simulation.getServer(), accountDTO)
                : initializeAdminFromUserManagement(simulation.getServer());
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
        if (adminAccount == null) {
            throw new IllegalStateException("No admin account found for server " + server.name());
        }
        var admin = SimulatedArtemisUser.createArtemisAdminFromUser(artemisConfiguration.getUrl(server), adminAccount, artemisUserService);
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

    private void cancelAllBuildJobs(SimulatedArtemisAdmin admin) {
        admin.cancelAllQueuedBuildJobs();
        admin.cancelAllRunningBuildJobs();
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
    private SimulatedArtemisStudent[] initializeStudents(SimulationRun simulationRun) {
        List<ArtemisUser> artemisUsers;
        Simulation simulation = simulationRun.getSimulation();

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
                    mechanism
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
    private List<RequestStat> performActionWithAll(int threadCount, int numberOfUsers, Function<Integer, List<RequestStat>> action) {
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(threadCount);
        Scheduler scheduler = Schedulers.from(threadPoolExecutor);
        List<RequestStat> requestStats = Collections.synchronizedList(new ArrayList<>());

        try {
            Flowable.range(0, numberOfUsers)
                .parallel(threadCount)
                .runOn(scheduler)
                .doOnNext(i -> {
                    try {
                        requestStats.addAll(action.apply(i));
                    } catch (Exception e) {
                        log.warn("Error while performing action for user {{}}: {{}}", i + 1, e.getMessage());
                    }
                })
                .sequential()
                .blockingSubscribe();
        } finally {
            threadPoolExecutor.shutdownNow();
            scheduler.shutdown();
        }
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

        logAndSend(false, simulationRun, "Trying to cancel all build jobs...");
        cancelAllBuildJobs(admin);
        if (!doNotSleep) {
            try {
                sleep(1_000 * 10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        logAndSend(false, simulationRun, "Done cancelling all build jobs");

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
        var message = String.format(format, args);
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
