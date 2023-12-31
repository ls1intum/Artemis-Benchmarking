package de.tum.cit.ase.service.simulation;

import static java.lang.Thread.sleep;

import de.tum.cit.ase.artemisModel.Course;
import de.tum.cit.ase.artemisModel.Exam;
import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.repository.LogMessageRepository;
import de.tum.cit.ase.repository.SimulationRunRepository;
import de.tum.cit.ase.service.MailService;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.service.artemis.ArtemisUserService;
import de.tum.cit.ase.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.ase.service.artemis.interaction.SimulatedArtemisStudent;
import de.tum.cit.ase.service.artemis.interaction.SimulatedArtemisUser;
import de.tum.cit.ase.util.ArtemisAccountDTO;
import de.tum.cit.ase.util.ArtemisServer;
import de.tum.cit.ase.web.websocket.SimulationWebsocketService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimulationRunExecutionService {

    private final Logger log = LoggerFactory.getLogger(SimulationRunExecutionService.class);

    private final SimulationWebsocketService simulationWebsocketService;
    private final ArtemisUserService artemisUserService;
    private final ArtemisConfiguration artemisConfiguration;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationResultService simulationResultService;
    private final LogMessageRepository logMessageRepository;
    private final MailService mailService;
    private boolean doNotSleep = false;

    public SimulationRunExecutionService(
        ArtemisConfiguration artemisConfiguration,
        SimulationWebsocketService simulationWebsocketService,
        ArtemisUserService artemisUserService,
        SimulationRunRepository simulationRunRepository,
        SimulationResultService simulationResultService,
        LogMessageRepository logMessageRepository,
        MailService mailService
    ) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationResultService = simulationResultService;
        this.logMessageRepository = logMessageRepository;
        this.artemisUserService = artemisUserService;
        this.mailService = mailService;
    }

    /**
     * Executes the given simulation run. This method is synchronized to prevent multiple simulations from running at the same time.
     * <p>
     * The steps of the simulation depend on the simulation mode, see {@link Simulation.Mode}.
     * This method sends status updates, log messages and results to the client via websockets.
     * @param simulationRun the simulation run to execute
     */
    public synchronized void simulateExam(SimulationRun simulationRun) {
        ArtemisAccountDTO accountDTO = simulationRun.getAdminAccount();
        SimulationSchedule schedule = simulationRun.getSchedule();

        simulationRun.setStatus(SimulationRun.Status.RUNNING);
        simulationRun = simulationRunRepository.save(simulationRun);
        simulationRun.setSchedule(schedule);
        simulationWebsocketService.sendRunStatusUpdate(simulationRun);

        var simulation = simulationRun.getSimulation();
        var courseId = simulation.getCourseId();
        var examId = simulation.getExamId();
        SimulatedArtemisAdmin admin = null;
        SimulatedArtemisStudent[] students;

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

        try {
            students = initializeStudents(simulation);
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while initializing students: %s", e.getMessage());
            failSimulationRun(simulationRun);
            return;
        }

        // Initialize admin if necessary
        if (simulation.getMode() != Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM) {
            try {
                logAndSend(false, simulationRun, "Initializing admin...");
                admin =
                    simulation.getServer() == ArtemisServer.PRODUCTION
                        ? initializeAdminWithAccount(simulation.getServer(), accountDTO)
                        : initializeAdmin(simulation.getServer());
            } catch (Exception e) {
                logAndSend(true, simulationRun, "Error while initializing admin: %s", e.getMessage());
                failSimulationRun(simulationRun);
                return;
            }

            Course course;
            // Create course if necessary
            if (simulation.getMode() == Simulation.Mode.CREATE_COURSE_AND_EXAM) {
                logAndSend(false, simulationRun, "Creating course...");
                try {
                    course = admin.createCourse();
                    courseId = course.getId();
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while creating course: %s", e.getMessage());
                    failSimulationRun(simulationRun);
                    return;
                }
                logAndSend(false, simulationRun, "Successfully created course. Course ID: %d", courseId);

                // Register students for course
                logAndSend(false, simulationRun, "Registering students for course...");
                try {
                    admin.registerStudentsForCourse(courseId, students);
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while registering students for course: %s", e.getMessage());
                    cleanupAsync(admin, simulationRun, courseId, 0);
                    failSimulationRun(simulationRun);
                    return;
                }

                if (!doNotSleep) {
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
                try {
                    course = admin.getCourse(courseId);
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while fetching course: %s", e.getMessage());
                    failSimulationRun(simulationRun);
                    return;
                }
            }

            // Create exam if necessary
            if (simulation.getMode() != Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM) {
                logAndSend(false, simulationRun, "Creating exam...");
                Exam exam;
                try {
                    exam = admin.createExam(course);
                    examId = exam.getId();
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while creating exam: %s", e.getMessage());
                    cleanupAsync(admin, simulationRun, courseId, 0);
                    failSimulationRun(simulationRun);
                    return;
                }
                logAndSend(false, simulationRun, "Successfully created exam. Exam ID: %d", examId);

                // Create exam exercises
                logAndSend(false, simulationRun, "Creating exam exercises...");
                try {
                    admin.createExamExercises(courseId, exam);
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while creating exam exercises: %s", e.getMessage());
                    cleanupAsync(admin, simulationRun, courseId, examId);
                    failSimulationRun(simulationRun);
                    return;
                }

                // Register students for exam
                logAndSend(false, simulationRun, "Registering students for exam...");
                try {
                    admin.registerStudentsForExam(courseId, examId);
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while registering students for exam: %s", e.getMessage());
                    cleanupAsync(admin, simulationRun, courseId, examId);
                    failSimulationRun(simulationRun);
                    return;
                }
            } else {
                logAndSend(false, simulationRun, "Using existing exam.");
            }

            // Prepare exam for conduction
            logAndSend(false, simulationRun, "Preparing exam for conduction...");
            try {
                admin.prepareExam(courseId, examId);
            } catch (Exception e) {
                logAndSend(true, simulationRun, "Error while preparing exam: %s", e.getMessage());
                cleanupAsync(admin, simulationRun, courseId, examId);
                failSimulationRun(simulationRun);
                return;
            }
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

        logAndSend(false, simulationRun, "Starting simulation...");

        int threadCount = Integer.min(Runtime.getRuntime().availableProcessors() * 10, simulation.getNumberOfUsers());
        logAndSend(false, simulationRun, "Using %d threads for simulation.", threadCount);

        List<RequestStat> requestStats = new ArrayList<>();

        try {
            logAndSend(false, simulationRun, "Logging in students...");
            requestStats.addAll(performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].login()));

            logAndSend(false, simulationRun, "Performing initial calls...");
            requestStats.addAll(performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].performInitialCalls()));

            logAndSend(false, simulationRun, "Participating in exam...");
            long finalCourseId = courseId;
            long finalExamId = examId;
            requestStats.addAll(
                performActionWithAll(
                    threadCount,
                    simulation.getNumberOfUsers(),
                    i -> students[i].startExamParticipation(finalCourseId, finalExamId)
                )
            );
            requestStats.addAll(
                performActionWithAll(
                    threadCount,
                    simulation.getNumberOfUsers(),
                    i -> students[i].participateInExam(finalCourseId, finalExamId)
                )
            );
            requestStats.addAll(
                performActionWithAll(
                    threadCount,
                    simulation.getNumberOfUsers(),
                    i -> students[i].submitAndEndExam(finalCourseId, finalExamId)
                )
            );
        } catch (Exception e) {
            logAndSend(true, simulationRun, "Error while performing simulation: %s", e.getMessage());
            cleanupAsync(admin, simulationRun, courseId, examId);
            failSimulationRun(simulationRun);
            return;
        }

        logAndSend(false, simulationRun, "Simulation finished.");
        cleanupAsync(admin, simulationRun, courseId, examId);
        SimulationRun runWithResult = simulationResultService.calculateAndSaveResult(simulationRun, requestStats);
        finishSimulationRun(runWithResult);
        sendRunResult(runWithResult);
    }

    /**
     * Sets the doNotSleep flag.
     * If the flag is set to true, the simulation will not wait for user group synchronization.
     * The flag should only be set to true for testing purposes when the connection to Artemis is mocked.
     * @param doNotSleep the value to set the flag to
     */
    public void setDoNotSleep(boolean doNotSleep) {
        this.doNotSleep = doNotSleep;
    }

    /**
     * Initializes the admin for the given server and logs in.
     *
     * @param server the Artemis Server to initialize the admin for
     * @return the initialized and logged in admin
     */
    private SimulatedArtemisAdmin initializeAdmin(ArtemisServer server) {
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
     * @param server the Artemis Server to initialize the admin for
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
     * Initializes the students for the simulation.
     * <p>
     * Note: This method does not log in the students.
     *
     * @param simulation the simulation to initialize the students for
     * @return an array of initialized students
     */
    private SimulatedArtemisStudent[] initializeStudents(Simulation simulation) {
        List<ArtemisUser> artemisUsers;
        if (simulation.isCustomizeUserRange()) {
            artemisUsers = artemisUserService.getUsersFromRange(simulation.getServer(), simulation.getUserRange());
        } else {
            artemisUsers = artemisUserService.getUsersFromRange(simulation.getServer(), "1-" + simulation.getNumberOfUsers());
        }

        SimulatedArtemisStudent[] users = new SimulatedArtemisStudent[artemisUsers.size()];
        for (int i = 0; i < artemisUsers.size(); i++) {
            users[i] =
                SimulatedArtemisUser.createArtemisStudent(
                    artemisConfiguration.getUrl(simulation.getServer()),
                    artemisUsers.get(i),
                    artemisUserService,
                    simulation.getNumberOfCommitsAndPushesFrom(),
                    simulation.getNumberOfCommitsAndPushesTo()
                );
        }
        return users;
    }

    /**
     * Performs the given action for all users in parallel with the given number of threads.
     * Collects all request stats and returns them as a list.
     * <p>
     * If an exception occurs while performing the action for a user, the exception is logged and the user is skipped.
     * Exceptions occurring for one user do not affect the execution of the action for other users and are not rethrown.
     *
     * @param threadCount the number of threads to use
     * @param numberOfUsers the number of users to perform the action for
     * @param action the action to perform
     * @return a list of request stats for all performed actions
     */
    private List<RequestStat> performActionWithAll(int threadCount, int numberOfUsers, Function<Integer, List<RequestStat>> action) {
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(threadCount);
        Scheduler scheduler = Schedulers.from(threadPoolExecutor);
        List<RequestStat> requestStats = Collections.synchronizedList(new ArrayList<>());

        try {
            Flowable
                .range(0, numberOfUsers)
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
     * @param admin the admin to use for cleanup
     * @param simulationRun the simulation run to cleanup
     * @param courseId the ID of the course to cleanup
     * @param examId the ID of the exam to cleanup
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
     * @param admin the admin to use for cleanup
     * @param simulationRun the simulation run to cleanup
     * @param courseId the ID of the course to cleanup
     * @param examId the ID of the exam to cleanup
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
        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        simulationWebsocketService.sendRunStatusUpdate(savedSimulationRun);
    }

    private void finishSimulationRun(SimulationRun simulationRun) {
        simulationRun.setStatus(SimulationRun.Status.FINISHED);
        SimulationRun savedSimulationRun = simulationRunRepository.save(simulationRun);
        simulationWebsocketService.sendRunStatusUpdate(savedSimulationRun);
    }

    private void sendRunResult(SimulationRun simulationRun) {
        simulationWebsocketService.sendSimulationResult(simulationRun);
        if (simulationRun.getSchedule() != null) {
            mailService.sendRunResultMail(simulationRun, simulationRun.getSchedule());
        }
    }
}
