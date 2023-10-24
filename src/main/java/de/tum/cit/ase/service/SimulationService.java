package de.tum.cit.ase.service;

import de.tum.cit.ase.service.util.RequestStat;
import de.tum.cit.ase.service.util.SimulationResult;
import de.tum.cit.ase.service.util.SyntheticArtemisUser;
import de.tum.cit.ase.service.util.TimeLogUtil;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private final Logger log = LoggerFactory.getLogger(SimulationService.class);

    @Value("${artemis.local.username_template}")
    private String testUserUsernameTemplate;

    @Value("${artemis.local.password_template}")
    private String testUserPasswordTemplate;

    private static final int maxNumberOfThreads = 20;
    private static final String courseId = "3";
    private static final String examId = "29";

    private final SimulationWebsocketService simulationWebsocketService;

    private boolean simulationRunning = false;

    public SimulationService(SimulationWebsocketService simulationWebsocketService) {
        this.simulationWebsocketService = simulationWebsocketService;
    }

    @Async
    public synchronized void simulateExam(int numberOfUsers) {
        simulationRunning = true;
        log.info("Starting simulation...");
        SyntheticArtemisUser[] users = initializeUsers(numberOfUsers);
        int threadCount = Integer.min(maxNumberOfThreads, numberOfUsers);
        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.addAll(performActionWithAll(20, numberOfUsers, i -> users[i].login()));
        requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> users[i].performInitialCalls()));
        requestStats.addAll(performActionWithAll(threadCount, numberOfUsers, i -> users[i].participateInExam(courseId, examId)));
        logRequestStatsPerMinute(requestStats);
        var simulationResult = new SimulationResult(requestStats);
        log.info("Simulation finished");
        System.out.println(simulationResult);
        simulationWebsocketService.sendSimulationResult(simulationResult);
        simulationRunning = false;
    }

    private SyntheticArtemisUser[] initializeUsers(int numberOfUsers) {
        SyntheticArtemisUser[] users = new SyntheticArtemisUser[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            var username = testUserUsernameTemplate.replace("{i}", String.valueOf(i + 1));
            var password = testUserPasswordTemplate.replace("{i}", String.valueOf(i + 1));
            users[i] = new SyntheticArtemisUser(username, password);
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
            .count()
            .blockingGet();

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

    public boolean isSimulationRunning() {
        return simulationRunning;
    }
}
