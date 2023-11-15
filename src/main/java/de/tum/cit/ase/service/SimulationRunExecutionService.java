package de.tum.cit.ase.service;

import static java.lang.Thread.sleep;

import de.tum.cit.ase.artemisModel.Course;
import de.tum.cit.ase.artemisModel.Exam;
import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.repository.LogMessageRepository;
import de.tum.cit.ase.repository.SimulationRunRepository;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.service.artemis.interaction.ArtemisAdmin;
import de.tum.cit.ase.service.artemis.interaction.ArtemisStudent;
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
    private final ArtemisConfiguration artemisConfiguration;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationResultService simulationResultService;
    private final LogMessageRepository logMessageRepository;

    public SimulationRunExecutionService(
        ArtemisConfiguration artemisConfiguration,
        SimulationWebsocketService simulationWebsocketService,
        SimulationRunRepository simulationRunRepository,
        SimulationResultService simulationResultService,
        LogMessageRepository logMessageRepository
    ) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationResultService = simulationResultService;
        this.logMessageRepository = logMessageRepository;
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

        simulationRun.setStatus(SimulationRun.Status.RUNNING);
        simulationRun = simulationRunRepository.save(simulationRun);
        simulationWebsocketService.sendRunStatusUpdate(simulationRun);

        var simulation = simulationRun.getSimulation();
        var courseId = simulation.getCourseId();
        var examId = simulation.getExamId();
        ArtemisAdmin admin;

        logAndSend(
            false,
            simulationRun,
            "Starting simulation with %d users on %s...",
            simulation.getNumberOfUsers(),
            simulation.getServer().name()
        );

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
                    admin.registerStudentsForCourse(
                        courseId,
                        simulation.getNumberOfUsers(),
                        artemisConfiguration.getUsernameTemplate(simulation.getServer())
                    );
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while registering students for course: %s", e.getMessage());
                    cleanup(admin, courseId, simulationRun);
                    failSimulationRun(simulationRun);
                    return;
                }

                // Wait for synchronization of user groups
                try {
                    logAndSend(false, simulationRun, "Waiting for synchronization of user groups (1 min)...");
                    sleep(1_000 * 60);
                } catch (InterruptedException ignored) {}
            } else {
                logAndSend(false, simulationRun, "Using existing course.");
                course = admin.getCourse(courseId);
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
                    cleanup(admin, courseId, simulationRun);
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
                    cleanup(admin, courseId, simulationRun);
                    failSimulationRun(simulationRun);
                    return;
                }

                // Register students for exam
                logAndSend(false, simulationRun, "Registering students for exam...");
                try {
                    admin.registerStudentsForExam(courseId, examId);
                } catch (Exception e) {
                    logAndSend(true, simulationRun, "Error while registering students for exam: %s", e.getMessage());
                    cleanup(admin, courseId, simulationRun);
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
                cleanup(admin, courseId, simulationRun);
                failSimulationRun(simulationRun);
                return;
            }
            try {
                // Wait for a couple of seconds. Without this, students cannot access their repos.
                // Not sure why this is necessary, trying to figure it out
                sleep(5_000);
            } catch (InterruptedException ignored) {}
            logAndSend(false, simulationRun, "Preparation finished...");
        } else {
            logAndSend(false, simulationRun, "Using existing course and exam. No admin required.");
        }

        logAndSend(false, simulationRun, "Starting simulation...");

        ArtemisStudent[] students = initializeStudents(simulation.getNumberOfUsers(), simulation.getServer());

        // We want to simulate with as many threads as possible, at least 80
        int minNumberOfReads = Integer.max(Runtime.getRuntime().availableProcessors() * 4, 80);
        int threadCount = Integer.min(minNumberOfReads, simulation.getNumberOfUsers());
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
            failSimulationRun(simulationRun);
            return;
        }

        logAndSend(false, simulationRun, "Simulation finished.");
        SimulationRun runWithResult = simulationResultService.calculateAndSaveResult(simulationRun, requestStats);
        finishSimulationRun(runWithResult);
        sendRunResult(runWithResult);
    }

    /**
     * Initializes the admin for the given server and logs in.
     *
     * @param server the Artemis Server to initialize the admin for
     * @return the initialized and logged in admin
     */
    private ArtemisAdmin initializeAdmin(ArtemisServer server) {
        var admin = new ArtemisAdmin(
            artemisConfiguration.getAdminUsername(server),
            artemisConfiguration.getAdminPassword(server),
            artemisConfiguration.getUrl(server)
        );
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
    private ArtemisAdmin initializeAdminWithAccount(ArtemisServer server, ArtemisAccountDTO artemisAccountDTO) {
        var admin = new ArtemisAdmin(artemisAccountDTO.getUsername(), artemisAccountDTO.getPassword(), artemisConfiguration.getUrl(server));
        admin.login();
        return admin;
    }

    /**
     * Initializes the given number of students with the given server.
     * <p>
     * Note: This method does not log in the students.
     *
     * @param numberOfUsers the number of students to initialize
     * @param server the Artemis Server to initialize the students for
     * @return an array of initialized students
     */
    private ArtemisStudent[] initializeStudents(int numberOfUsers, ArtemisServer server) {
        ArtemisStudent[] users = new ArtemisStudent[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            var username = artemisConfiguration.getUsernameTemplate(server).replace("{i}", String.valueOf(i + 1));
            var password = artemisConfiguration.getPasswordTemplate(server).replace("{i}", String.valueOf(i + 1));
            users[i] = new ArtemisStudent(username, password, artemisConfiguration.getUrl(server));
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

        threadPoolExecutor.shutdownNow();
        scheduler.shutdown();
        return requestStats;
    }

    private void cleanup(ArtemisAdmin admin, long courseId, SimulationRun simulationRun) {
        logAndSend(false, simulationRun, "Cleanup is currently disabled.");
    }

    private void logAndSend(boolean error, SimulationRun simulationRun, String format, Object... args) {
        var message = String.format(format, args);
        log.info(message);
        LogMessage logMessage = new LogMessage();
        logMessage.setSimulationRun(simulationRun);
        logMessage.setMessage(message);
        logMessage.setError(error);
        logMessage.setTimestamp(ZonedDateTime.now());
        LogMessage savedLogMessage = logMessageRepository.save(logMessage);
        simulationWebsocketService.sendRunLogMessage(simulationRun, savedLogMessage);
    }

    private void failSimulationRun(SimulationRun simulationRun) {
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
    }
}
