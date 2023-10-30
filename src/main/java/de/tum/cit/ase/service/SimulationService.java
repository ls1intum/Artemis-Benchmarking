package de.tum.cit.ase.service;

import static java.lang.Thread.sleep;

import de.tum.cit.ase.artemisModel.Exam;
import de.tum.cit.ase.config.ArtemisConfiguration;
import de.tum.cit.ase.service.util.*;
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

    private static final int maxNumberOfThreads = 20;

    private final SimulationWebsocketService simulationWebsocketService;

    private final ArtemisConfiguration artemisConfiguration;

    public SimulationService(ArtemisConfiguration artemisConfiguration, SimulationWebsocketService simulationWebsocketService) {
        this.simulationWebsocketService = simulationWebsocketService;
        this.artemisConfiguration = artemisConfiguration;
    }

    @Async
    public synchronized void simulateExam(int numberOfUsers, long courseId, long examId, ArtemisServer server) {
        boolean cleanupNeeded = false;
        try {
            log.info("Starting preparation...");
            if (courseId == 0L && examId == 0L) {
                log.info("No course and exam IDs provided, creating new course and exam...");
                var exam = createCourseAndExam(numberOfUsers, server);
                courseId = exam.getCourse().getId();
                examId = exam.getId();
                cleanupNeeded = true;
            }
            prepareExamForSimulation(courseId, examId, server);
        } catch (Exception e) {
            log.error("Error while preparing exam, aborting simulation: {{}}", e.getMessage());
            simulationWebsocketService.sendSimulationError("An error occurred while preparing the exam for simulation.\n" + e.getMessage());
            return;
        }
        log.info("Preparation finished. Waiting for 10sec...");
        try {
            // Wait for a couple of seconds. Without this, students cannot access their repos.
            // Not sure why this is necessary, trying to figure it out
            sleep(5_000);
        } catch (InterruptedException ignored) {}

        log.info("Starting simulation...");
        SyntheticArtemisUser[] users = initializeUsers(numberOfUsers, server);
        int threadCount = Integer.min(maxNumberOfThreads, numberOfUsers);
        List<RequestStat> requestStats = new ArrayList<>();

        try {
            requestStats.addAll(performActionWithAll(20, numberOfUsers, i -> users[i].login()));
            requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> users[i].performInitialCalls()));

            long finalCourseId = courseId;
            long finalExamId = examId;
            requestStats.addAll(
                performActionWithAll(threadCount, numberOfUsers, i -> users[i].participateInExam(finalCourseId, finalExamId))
            );

            logRequestStatsPerMinute(requestStats);
            var simulationResult = new SimulationResult(requestStats);
            log.info("Simulation finished");
            simulationWebsocketService.sendSimulationResult(simulationResult);

            if (cleanupNeeded) {
                cleanup(courseId, server);
            }
        } catch (Exception e) {
            log.error("Error during simulation {{}}", e.getMessage());
        }
    }

    private void cleanup(long courseId, ArtemisServer server) {
        SyntheticArtemisUser admin = new SyntheticArtemisUser(
            artemisConfiguration.getAdminUsername(server),
            artemisConfiguration.getAdminPassword(server),
            artemisConfiguration.getUrl(server)
        );
        admin.login();
        admin.deleteCourse(courseId);
    }

    private Exam createCourseAndExam(int numberOfUsers, ArtemisServer server) {
        // Login as admin
        SyntheticArtemisUser admin = new SyntheticArtemisUser(
            artemisConfiguration.getAdminUsername(server),
            artemisConfiguration.getAdminPassword(server),
            artemisConfiguration.getUrl(server)
        );
        admin.login();

        // Create course and exam
        var course = admin.createCourse();
        var exam = admin.createExam(course);

        try {
            sleep(1000 * 60 * 3); //Wait for 3 minutes until user groups are synchronized
        } catch (InterruptedException ignored) {}

        // Create exam exercises and register students
        admin.createExamExercises(course.getId(), exam);
        admin.registerStudentsForCourseAndExam(
            course.getId(),
            exam.getId(),
            numberOfUsers,
            artemisConfiguration.getUsernameTemplate(server)
        );
        return exam;
    }

    private void prepareExamForSimulation(long courseId, long examId, ArtemisServer server) {
        SyntheticArtemisUser admin = new SyntheticArtemisUser(
            artemisConfiguration.getAdminUsername(server),
            artemisConfiguration.getAdminPassword(server),
            artemisConfiguration.getUrl(server)
        );
        admin.login();
        admin.prepareExam(courseId, examId);
    }

    private SyntheticArtemisUser[] initializeUsers(int numberOfUsers, ArtemisServer server) {
        SyntheticArtemisUser[] users = new SyntheticArtemisUser[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            var username = artemisConfiguration.getUsernameTemplate(server).replace("{i}", String.valueOf(i + 1));
            var password = artemisConfiguration.getPasswordTemplate(server).replace("{i}", String.valueOf(i + 1));
            users[i] = new SyntheticArtemisUser(username, password, artemisConfiguration.getUrl(server));
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
            .doOnNext(i -> requestStats.addAll(action.apply(i)))
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
