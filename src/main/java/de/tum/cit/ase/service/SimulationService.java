package de.tum.cit.ase.service;

import static java.lang.Thread.sleep;

import de.tum.cit.ase.artemisModel.Course;
import de.tum.cit.ase.artemisModel.Exam;
import de.tum.cit.ase.domain.RequestStat;
import de.tum.cit.ase.domain.SimulationResult;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.service.artemis.ArtemisServer;
import de.tum.cit.ase.service.artemis.interaction.ArtemisAdmin;
import de.tum.cit.ase.service.artemis.interaction.ArtemisStudent;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final SimulationWebsocketService simulationWebsocketService;
    private final ArtemisConfiguration artemisConfiguration;

    public SimulationService(ArtemisConfiguration artemisConfiguration, SimulationWebsocketService simulationWebsocketService) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
    }

    @Async
    public synchronized void simulateExam(int numberOfUsers, long courseId, long examId, ArtemisServer server) {
        boolean cleanupNeeded = false;
        ArtemisAdmin admin;

        logAndSendInfo("Starting simulation with %d users on %s...", numberOfUsers, server.name());

        if (server == ArtemisServer.PRODUCTION) {
            simulateExamOnProduction(numberOfUsers, examId, courseId);
            return;
        }

        try {
            logAndSendInfo("Initializing admin...");
            admin = initializeAdmin(server);
        } catch (Exception e) {
            logAndSendError("Error while initializing admin: %s", e.getMessage());
            simulationWebsocketService.sendSimulationFailed();
            return;
        }

        if (courseId == 0L && examId == 0L) {
            logAndSendInfo("No course and exam specified. Creating course and exam...");
            cleanupNeeded = true;
            Course course;

            // Create course
            try {
                course = admin.createCourse();
            } catch (Exception e) {
                logAndSendError("Error while creating course: %s", e.getMessage());
                simulationWebsocketService.sendSimulationFailed();
                return;
            }
            logAndSendInfo("Successfully created course. Course ID: %d", course.getId());
            // Create exam
            try {
                var exam = createAndInitializeExam(numberOfUsers, server, admin, course);
                courseId = exam.getCourse().getId();
                examId = exam.getId();
            } catch (Exception e) {
                logAndSendError("Error while creating exam: %s", e.getMessage());
                cleanup(admin, course.getId());
                simulationWebsocketService.sendSimulationFailed();
                return;
            }
            logAndSendInfo("Successfully initialized exam. Exam ID: %d", examId);
        } else {
            logAndSendInfo("Using existing course %d and exam %d.", courseId, examId);
        }
        try {
            logAndSendInfo("Preparing exam for simulation...");
            admin.prepareExam(courseId, examId);
        } catch (Exception e) {
            logAndSendError("Error while preparing exam: %s", e.getMessage());
            if (cleanupNeeded) {
                cleanup(admin, courseId);
            }
            simulationWebsocketService.sendSimulationFailed();
            return;
        }

        logAndSendInfo("Preparation finished...");
        try {
            // Wait for a couple of seconds. Without this, students cannot access their repos.
            // Not sure why this is necessary, trying to figure it out
            sleep(5_000);
        } catch (InterruptedException ignored) {}

        logAndSendInfo("Starting simulation...");

        ArtemisStudent[] students = initializeStudents(numberOfUsers, server);
        int threadCount = Integer.min(Runtime.getRuntime().availableProcessors() * 4, numberOfUsers);
        logAndSendInfo("Using %d threads for simulation.", threadCount);

        List<RequestStat> requestStats = new ArrayList<>();

        try {
            logAndSendInfo("Logging in students...");
            requestStats.addAll(performActionWithAll(20, numberOfUsers, i -> students[i].login()));

            logAndSendInfo("Performing initial calls...");
            requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> students[i].performInitialCalls()));

            logAndSendInfo("Participating in exam...");
            long finalCourseId = courseId;
            long finalExamId = examId;
            requestStats.addAll(
                performActionWithAll(threadCount, numberOfUsers, i -> students[i].participateInExam(finalCourseId, finalExamId))
            );
        } catch (Exception e) {
            logAndSendError("Error while performing simulation: %s", e.getMessage());
            if (cleanupNeeded) {
                cleanup(admin, courseId);
            }
            simulationWebsocketService.sendSimulationFailed();
            return;
        }

        logRequestStatsPerMinute(requestStats);
        var simulationResult = new SimulationResult(requestStats);
        logAndSendInfo("Simulation finished.");

        if (cleanupNeeded) {
            cleanup(admin, courseId);
        }
        simulationWebsocketService.sendSimulationResult(simulationResult);
    }

    private void simulateExamOnProduction(int numberOfUsers, long examId, long courseId) {
        logAndSendInfo("Starting simulation...");

        ArtemisStudent[] students = initializeStudents(numberOfUsers, ArtemisServer.PRODUCTION);
        int threadCount = Integer.min(Runtime.getRuntime().availableProcessors() * 4, numberOfUsers);
        logAndSendInfo("Using %d threads for simulation.", threadCount);

        List<RequestStat> requestStats = new ArrayList<>();

        try {
            logAndSendInfo("Logging in students...");
            requestStats.addAll(performActionWithAll(20, numberOfUsers, i -> students[i].login()));

            logAndSendInfo("Performing initial calls...");
            requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> students[i].performInitialCalls()));

            logAndSendInfo("Participating in exam...");
            requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> students[i].participateInExam(courseId, examId)));
        } catch (Exception e) {
            logAndSendError("Error while performing simulation: %s", e.getMessage());
            simulationWebsocketService.sendSimulationFailed();
            return;
        }

        logRequestStatsPerMinute(requestStats);
        var simulationResult = new SimulationResult(requestStats);
        logAndSendInfo("Simulation finished.");

        simulationWebsocketService.sendSimulationResult(simulationResult);
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

    private Exam createAndInitializeExam(int numberOfUsers, ArtemisServer server, ArtemisAdmin admin, Course course) {
        var exam = admin.createExam(course);

        logAndSendInfo("Successfully created course and exam. Waiting for synchronization of user groups (3 min)...");
        try {
            sleep(1000 * 60 * 3); //Wait for 3 minutes until user groups are synchronized
        } catch (InterruptedException ignored) {}

        // Create exam exercises and register students
        logAndSendInfo("Creating exam exercises...");
        admin.createExamExercises(course.getId(), exam);
        logAndSendInfo("Registering students...");
        admin.registerStudentsForCourseAndExam(
            course.getId(),
            exam.getId(),
            numberOfUsers,
            artemisConfiguration.getUsernameTemplate(server)
        );
        return exam;
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
        logAndSendInfo("Cleaning up... Depending on the number of users, this may take a few minutes.");
        try {
            sleep(1000 * 10); // Give the server a few seconds to recover
            admin.deleteCourse(courseId);
            logAndSendInfo("Successfully cleaned up.");
        } catch (Exception e) {
            logAndSendError("Error while cleaning up: %s", e.getMessage());
            logAndSendError(
                "The deletion of the course failed, potentially due to overloading of the Artemis Server. Please wait a few minutes and then delete the course 'Temporary Benchmarking Exam' manually. If the course is already deleted, make sure that the project 'benchmark Programming Exercise for Benchmarking' is deleted from the VCS as well."
            );
        }
    }

    private void logRequestStatsPerMinute(List<RequestStat> requestStats) {
        log.info(
            "Average time for {}x {}: {}, rates: {}",
            requestStats.size(),
            "request",
            TimeLogUtil.formatDuration(getAverage(requestStats)),
            getRatesPerMinute(requestStats)
        );
    }

    private String getRatesPerMinute(List<RequestStat> requestStats) {
        var formatter = DateTimeFormatter.ofPattern("HH:mm");
        return requestStats
            .stream()
            .map(RequestStat::dateTime)
            .collect(Collectors.groupingBy(date -> date.format(formatter), TreeMap::new, Collectors.counting()))
            .toString();
    }

    private String getRatesPerSecond(List<RequestStat> requestStats) {
        var formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return requestStats
            .stream()
            .map(RequestStat::dateTime)
            .collect(Collectors.groupingBy(date -> date.format(formatter), TreeMap::new, Collectors.counting()))
            .toString();
    }

    private long getAverage(Collection<RequestStat> times) {
        if (times.isEmpty()) {
            return 0;
        }
        return times.stream().map(RequestStat::duration).reduce(0L, Long::sum) / times.size();
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
