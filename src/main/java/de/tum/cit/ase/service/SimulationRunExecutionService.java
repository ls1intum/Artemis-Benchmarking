package de.tum.cit.ase.service;

import static java.lang.Thread.sleep;

import de.tum.cit.ase.artemisModel.Course;
import de.tum.cit.ase.artemisModel.Exam;
import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.repository.SimulationRunRepository;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.service.artemis.interaction.ArtemisAdmin;
import de.tum.cit.ase.service.artemis.interaction.ArtemisStudent;
import de.tum.cit.ase.util.ArtemisAccountDTO;
import de.tum.cit.ase.util.ArtemisServer;
import de.tum.cit.ase.util.TimeLogUtil;
import de.tum.cit.ase.web.websocket.SimulationWebsocketService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    public SimulationRunExecutionService(
        ArtemisConfiguration artemisConfiguration,
        SimulationWebsocketService simulationWebsocketService,
        SimulationRunRepository simulationRunRepository,
        SimulationResultService simulationResultService
    ) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationResultService = simulationResultService;
    }

    public synchronized void simulateExam(SimulationRun simulationRun) {
        simulationRun.setStatus(SimulationRun.Status.RUNNING);
        simulationRunRepository.save(simulationRun);

        var simulation = simulationRun.getSimulation();
        var courseId = simulation.getCourseId();
        var examId = simulation.getExamId();
        ArtemisAdmin admin = null;

        logAndSendInfo("Starting simulation with %d users on %s...", simulation.getNumberOfUsers(), simulation.getServer().name());

        // Initialize admin if necessary
        if (simulation.getMode() != Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM) {
            try {
                logAndSendInfo("Initializing admin...");
                admin =
                    simulation.getServer() == ArtemisServer.PRODUCTION
                        ? initializeAdminWithAccount(simulation.getServer(), simulationRun.getAdminAccount())
                        : initializeAdmin(simulation.getServer());
            } catch (Exception e) {
                logAndSendError("Error while initializing admin: %s", e.getMessage());
                simulationWebsocketService.sendSimulationFailed();
                return;
            }

            Course course;
            // Create course if necessary
            if (simulation.getMode() == Simulation.Mode.CREATE_COURSE_AND_EXAM) {
                logAndSendInfo("Creating course...");
                try {
                    course = admin.createCourse();
                    courseId = course.getId();
                } catch (Exception e) {
                    logAndSendError("Error while creating course: %s", e.getMessage());
                    simulationWebsocketService.sendSimulationFailed();
                    return;
                }
                logAndSendInfo("Successfully created course. Course ID: %d", courseId);

                // Register students for course
                logAndSendInfo("Registering students for course...");
                try {
                    admin.registerStudentsForCourse(
                        courseId,
                        simulation.getNumberOfUsers(),
                        artemisConfiguration.getUsernameTemplate(simulation.getServer())
                    );
                } catch (Exception e) {
                    logAndSendError("Error while registering students for course: %s", e.getMessage());
                    cleanup(admin, courseId);
                    simulationWebsocketService.sendSimulationFailed();
                    return;
                }
            } else {
                logAndSendInfo("Using existing course.");
                course = admin.getCourse(courseId);
            }

            // Create exam if necessary
            if (simulation.getMode() != Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM) {
                logAndSendInfo("Creating exam...");
                Exam exam;
                try {
                    exam = admin.createExam(course);
                    examId = exam.getId();
                } catch (Exception e) {
                    logAndSendError("Error while creating exam: %s", e.getMessage());
                    cleanup(admin, courseId);
                    simulationWebsocketService.sendSimulationFailed();
                    return;
                }
                logAndSendInfo("Successfully created exam. Exam ID: %d", examId);

                // Create exam exercises
                logAndSendInfo("Creating exam exercises...");
                try {
                    admin.createExamExercises(courseId, exam);
                } catch (Exception e) {
                    logAndSendError("Error while creating exam exercises: %s", e.getMessage());
                    cleanup(admin, courseId);
                    simulationWebsocketService.sendSimulationFailed();
                    return;
                }

                // Register students for exam
                logAndSendInfo("Registering students for exam...");
                try {
                    admin.registerStudentsForExam(courseId, examId);
                } catch (Exception e) {
                    logAndSendError("Error while registering students for exam: %s", e.getMessage());
                    cleanup(admin, courseId);
                    simulationWebsocketService.sendSimulationFailed();
                    return;
                }
            } else {
                logAndSendInfo("Using existing exam.");
            }

            // Prepare exam for conduction
            logAndSendInfo("Preparing exam for conduction...");
            try {
                admin.prepareExam(courseId, examId);
            } catch (Exception e) {
                logAndSendError("Error while preparing exam: %s", e.getMessage());
                cleanup(admin, courseId);
                simulationWebsocketService.sendSimulationFailed();
                return;
            }
            try {
                // Wait for a couple of seconds. Without this, students cannot access their repos.
                // Not sure why this is necessary, trying to figure it out
                sleep(5_000);
            } catch (InterruptedException ignored) {}
            logAndSendInfo("Preparation finished...");
        } else {
            logAndSendInfo("Using existing course and exam. No admin required.");
        }

        logAndSendInfo("Starting simulation...");

        ArtemisStudent[] students = initializeStudents(simulation.getNumberOfUsers(), simulation.getServer());

        // We want to simulate with as many threads as possible, at least 80
        int minNumberOfReads = Integer.max(Runtime.getRuntime().availableProcessors() * 4, 80);
        int threadCount = Integer.min(minNumberOfReads, simulation.getNumberOfUsers());
        logAndSendInfo("Using %d threads for simulation.", threadCount);

        List<RequestStat> requestStats = new ArrayList<>();

        try {
            logAndSendInfo("Logging in students...");
            requestStats.addAll(performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].login()));

            logAndSendInfo("Performing initial calls...");
            requestStats.addAll(performActionWithAll(threadCount, simulation.getNumberOfUsers(), i -> students[i].performInitialCalls()));

            logAndSendInfo("Participating in exam...");
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
            logAndSendError("Error while performing simulation: %s", e.getMessage());
            simulationWebsocketService.sendSimulationFailed();
            return;
        }

        logAndSendInfo("Simulation finished.");
        SimulationRun runWithResult = simulationResultService.calculateAndSaveResult(simulationRun, requestStats);
        simulationWebsocketService.sendSimulationCompleted();
    }

    private ArtemisAdmin initializeAdmin(ArtemisServer server) {
        var admin = new ArtemisAdmin(
            artemisConfiguration.getAdminUsername(server),
            artemisConfiguration.getAdminPassword(server),
            artemisConfiguration.getUrl(server)
        );
        admin.login();
        return admin;
    }

    private ArtemisAdmin initializeAdminWithAccount(ArtemisServer server, ArtemisAccountDTO artemisAccountDTO) {
        var admin = new ArtemisAdmin(artemisAccountDTO.getUsername(), artemisAccountDTO.getPassword(), artemisConfiguration.getUrl(server));
        admin.login();
        return admin;
    }

    private ArtemisStudent[] initializeStudents(int numberOfUsers, ArtemisServer server) {
        ArtemisStudent[] users = new ArtemisStudent[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            var username = artemisConfiguration.getUsernameTemplate(server).replace("{i}", String.valueOf(i + 1));
            var password = artemisConfiguration.getPasswordTemplate(server).replace("{i}", String.valueOf(i + 1));
            users[i] = new ArtemisStudent(username, password, artemisConfiguration.getUrl(server));
        }
        return users;
    }

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

    private void cleanup(ArtemisAdmin admin, long courseId) {
        logAndSendInfo("Cleanup is currently disabled.");
    }

    private void logAndSendInfo(String format, Object... args) {
        var message = String.format(format, args);
        log.info(message);
        simulationWebsocketService.sendSimulationInfo(message);
    }

    private void logAndSendError(String format, Object... args) {
        var message = String.format(format, args);
        log.error(message);
        simulationWebsocketService.sendSimulationError(message);
    }
}
