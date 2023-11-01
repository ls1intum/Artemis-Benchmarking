package de.tum.cit.ase.service;

import static java.lang.Thread.sleep;

import de.tum.cit.ase.artemisModel.Course;
import de.tum.cit.ase.artemisModel.Exam;
import de.tum.cit.ase.config.ArtemisConfiguration;
import de.tum.cit.ase.domain.SimulationResult;
import de.tum.cit.ase.web.artemis.ArtemisServer;
import de.tum.cit.ase.web.artemis.RequestStat;
import de.tum.cit.ase.web.artemis.interaction.ArtemisAdmin;
import de.tum.cit.ase.web.artemis.interaction.ArtemisStudent;
import de.tum.cit.ase.web.artemis.util.TimeLogUtil;
import de.tum.cit.ase.web.websocket.AdminWebsocketSessionHandler;
import de.tum.cit.ase.web.websocket.ArtemisWebsocketService;
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
    private final ArtemisWebsocketService artemisWebsocketService;
    private final ArtemisConfiguration artemisConfiguration;

    public SimulationService(
        ArtemisConfiguration artemisConfiguration,
        SimulationWebsocketService simulationWebsocketService,
        ArtemisWebsocketService artemisWebsocketService
    ) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
        this.artemisWebsocketService = artemisWebsocketService;
    }

    @Async
    public synchronized void simulateExam(int numberOfUsers, long courseId, long examId, ArtemisServer server) {
        boolean cleanupNeeded = false;
        ArtemisAdmin admin;

        log.info("Starting preparation...");
        try {
            log.info("Initializing admin...");
            admin = initializeAdmin(server);
            artemisWebsocketService.initializeConnection(server, admin.getAuthToken(), new AdminWebsocketSessionHandler(examId));
        } catch (Exception e) {
            log.error("Error while initializing admin: {}", e.getMessage());
            simulationWebsocketService.sendSimulationError("Error while initializing admin: " + e.getMessage());
            return;
        }

        if (courseId == 0L && examId == 0L) {
            log.info("No course and exam IDs provided, creating new course and exam...");
            cleanupNeeded = true;
            Course course;

            // Create course
            try {
                course = admin.createCourse();
            } catch (Exception e) {
                log.error("Error while creating course: {}", e.getMessage());
                simulationWebsocketService.sendSimulationError("Error while creating course: " + e.getMessage());
                return;
            }
            log.info("Successfully created course. Creating exam...");
            // Create exam
            try {
                var exam = createAndInitializeExam(numberOfUsers, server, admin, course);
                courseId = exam.getCourse().getId();
                examId = exam.getId();
            } catch (Exception e) {
                log.error("Error while creating exam: {}", e.getMessage());
                admin.deleteCourse(course.getId());
                simulationWebsocketService.sendSimulationError("Error while creating course and exam: " + e.getMessage());
                return;
            }
        }
        try {
            admin.prepareExam(courseId, examId);
        } catch (Exception e) {
            log.error("Error while preparing exam: {}", e.getMessage());
            if (cleanupNeeded) {
                admin.deleteCourse(courseId);
            }
            simulationWebsocketService.sendSimulationError("Error while preparing exam: " + e.getMessage());
            return;
        }

        log.info("Preparation finished. Waiting for 10sec...");
        try {
            // Wait for a couple of seconds. Without this, students cannot access their repos.
            // Not sure why this is necessary, trying to figure it out
            sleep(5_000);
        } catch (InterruptedException ignored) {}

        log.info("Starting simulation...");
        ArtemisStudent[] students = initializeStudents(numberOfUsers, server);
        int threadCount = Integer.min(Runtime.getRuntime().availableProcessors() * 4, numberOfUsers);
        log.info("Using {} threads for simulation", threadCount);
        List<RequestStat> requestStats = new ArrayList<>();

        try {
            requestStats.addAll(performActionWithAll(20, numberOfUsers, i -> students[i].login()));
            requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> students[i].performInitialCalls()));

            long finalCourseId = courseId;
            long finalExamId = examId;
            requestStats.addAll(
                performActionWithAll(threadCount, numberOfUsers, i -> students[i].participateInExam(finalCourseId, finalExamId))
            );
        } catch (Exception e) {
            log.error("Error while performing simulation: {}", e.getMessage());
            if (cleanupNeeded) {
                admin.deleteCourse(courseId);
            }
            simulationWebsocketService.sendSimulationError("Error while performing simulation: " + e.getMessage());
            return;
        }

        logRequestStatsPerMinute(requestStats);
        var simulationResult = new SimulationResult(requestStats);
        log.info("Simulation finished");

        if (cleanupNeeded) {
            log.info("Starting cleanup...");
            try {
                admin.deleteCourse(courseId);
            } catch (Exception e) {
                log.error("Error during cleanup: {}", e.getMessage());
                simulationWebsocketService.sendSimulationError("Error during cleanup: " + e.getMessage());
                return;
            }
            log.info("Cleanup finished.");
        }
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

        log.info("Successfully created course and exam. Waiting for synchronization of user groups...");
        try {
            sleep(1000 * 60 * 3); //Wait for 3 minutes until user groups are synchronized
        } catch (InterruptedException ignored) {}

        // Create exam exercises and register students
        log.info("Creating exam exercises...");
        admin.createExamExercises(course.getId(), exam);
        log.info("Registering students...");
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
}
